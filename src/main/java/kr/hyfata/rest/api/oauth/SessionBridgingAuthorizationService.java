package kr.hyfata.rest.api.oauth;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.session.entity.LoginHistory;
import kr.hyfata.rest.api.user.User;
import kr.hyfata.rest.api.session.entity.UserSession;
import kr.hyfata.rest.api.session.repository.LoginHistoryRepository;
import kr.hyfata.rest.api.user.UserRepository;
import kr.hyfata.rest.api.session.service.SessionService;
import kr.hyfata.rest.api.infrastructure.util.DeviceDetector;
import kr.hyfata.rest.api.infrastructure.util.GeoIpService;
import kr.hyfata.rest.api.infrastructure.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;

/**
 * SAS OAuth2AuthorizationService Session bridging decorator
 * <p>
 * SAS의 토큰 저장/삭제 시점에 기존 user_sessions 세션 관리를 연동한다:
 * <p>
 * 순환 의존 방지: 이 데코레이터는 SessionService의 낮은 수준 메서드
 * (createSasSession, findByAuthorizationId, revokeSessionEntity)만 사용하며,
 * SessionService → authorizationService 방향은 ObjectProvider 지연 주입으로 해결된다.
 */
@Component
@Slf4j
public class SessionBridgingAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final IpUtil ipUtil;
    private final DeviceDetector deviceDetector;
    private final GeoIpService geoIpService;

    public SessionBridgingAuthorizationService(JdbcTemplate jdbcTemplate,
                                               RegisteredClientRepository registeredClientRepository,
                                               SessionService sessionService,
                                               UserRepository userRepository,
                                               LoginHistoryRepository loginHistoryRepository,
                                               IpUtil ipUtil,
                                               DeviceDetector deviceDetector,
                                               GeoIpService geoIpService) {
        this.delegate = new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.ipUtil = ipUtil;
        this.deviceDetector = deviceDetector;
        this.geoIpService = geoIpService;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
        try {
            bridgeSave(authorization);
        } catch (Exception e) {
            // 토큰 발급 자체는 이미 완료됐으므로 브리징 실패는 로그만 남긴다
            log.error("세션 브리징(save) 실패: authorizationId={}", authorization.getId(), e);
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
        try {
            sessionService.findByAuthorizationId(authorization.getId())
                    .ifPresent(sessionService::revokeSessionEntity);
        } catch (Exception e) {
            log.error("세션 브리징(remove) 실패: authorizationId={}", authorization.getId(), e);
        }
    }

    @Override
    @Nullable
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    @Nullable
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    /**
     * refresh token이 새로 포함된 저장인 경우 세션을 미러링한다.
     * <p>
     * 신규 발급 vs 로테이션 구분: SAS는 로테이션 시에도 동일한 authorization ID를 유지한 채
     * refresh token 값만 교체한다. 따라서 authorization ID로 기존 세션을 찾아
     * - 없으면: 최초 발급 → 세션 생성 + LoginHistory 기록
     * - 있고 refresh token 해시가 다르다면: 로테이션 → 이전 세션 무효화 후 새 세션으로 교체
     * - 있고 해시가 같으면: 단순 재저장(토큰 무효화 플래그 갱신 등) → 아무 작업 안 함
     */
    private void bridgeSave(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        if (refreshToken == null) {
            return;  // authorization code 단계 저장 등 — 아직 토큰 미포함
        }

        String refreshTokenValue = refreshToken.getToken().getTokenValue();
        String newHash = sessionService.hashToken(refreshTokenValue);

        Optional<UserSession> existing = sessionService.findByAuthorizationId(authorization.getId());
        if (existing.isPresent() && existing.get().getRefreshTokenHash().equals(newHash)) {
            return;  // 동일 refresh token 재저장 (로테이션 아님)
        }

        User user = userRepository.findByEmail(authorization.getPrincipalName()).orElse(null);
        if (user == null) {
            log.warn("세션 브리징 대상 사용자를 찾을 수 없습니다: {}", authorization.getPrincipalName());
            return;
        }

        String accessTokenJti = extractAccessTokenJti(authorization);
        HttpServletRequest request = currentRequest();

        if (existing.isPresent()) {
            // refresh token 로테이션: 이전 세션 무효화(JTI 블랙리스트 포함) 후 새 세션으로 교체
            sessionService.revokeSessionEntity(existing.get());
            log.info("Refresh token rotated. Session replaced: authorizationId={}", authorization.getId());
        } else {
            // 토큰 최초 발급: 로그인 이력 기록
            recordLoginHistory(user, request);
        }

        // registeredClientId는 clientId와 동일하게 매핑되어 있음 (JpaRegisteredClientRepository)
        sessionService.createSasSession(user, refreshTokenValue, accessTokenJti,
                authorization.getRegisteredClientId(), authorization.getId(),
                authorization.getAuthorizedScopes(), request);
    }

    /**
     * Access Token의 JTI 추출.
     * SAS는 JWT access token의 claims를 token metadata에 저장하므로 우선 활용하고,
     * 없으면 JWT를 직접 파싱한다 (서명 검증 없이 클레임만 추출).
     */
    @Nullable
    private String extractAccessTokenJti(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken == null) {
            return null;
        }

        Map<String, Object> claims = accessToken.getClaims();
        if (claims != null && claims.get("jti") instanceof String jti) {
            return jti;
        }

        try {
            return SignedJWT.parse(accessToken.getToken().getTokenValue())
                    .getJWTClaimsSet().getJWTID();
        } catch (Exception e) {
            log.warn("Access token JTI 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰 최초 발급 시점의 로그인 이력 기록
     */
    private void recordLoginHistory(User user, @Nullable HttpServletRequest request) {
        String ipAddress = "unknown";
        String userAgent = null;
        if (request != null) {
            ipAddress = ipUtil.normalizeIp(ipUtil.getClientIp(request));
            userAgent = request.getHeader("User-Agent");
        }
        DeviceDetector.DeviceInfo deviceInfo = deviceDetector.parse(userAgent);

        loginHistoryRepository.save(LoginHistory.builder()
                .user(user)
                .ipAddress(ipAddress)
                .location(geoIpService.resolveLocation(ipAddress))
                .deviceType(deviceInfo.getDeviceType())
                .userAgent(userAgent)
                .success(true)
                .build());
    }

    /**
     * 현재 HTTP 요청 조회. 스케줄러 등 요청 컨텍스트가 없는 경우 null 반환.
     */
    @Nullable
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
