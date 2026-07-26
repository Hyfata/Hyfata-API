package kr.hyfata.rest.api.auth.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.auth.dto.UserSessionDTO;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.entity.UserSession;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.repository.UserSessionRepository;
import kr.hyfata.rest.api.auth.service.SessionService;
import kr.hyfata.rest.api.auth.service.TokenBlacklistService;
import kr.hyfata.rest.api.common.util.DeviceDetector;
import kr.hyfata.rest.api.common.util.GeoIpService;
import kr.hyfata.rest.api.common.util.IpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    /** SAS TokenSettings의 access token TTL과 동일 (15분) — JTI 블랙리스트 TTL로 사용 */
    private static final long SAS_ACCESS_TOKEN_TTL_SECONDS = 900;

    /** SAS TokenSettings의 refresh token TTL과 동일 (14일) */
    private static final long SAS_REFRESH_TOKEN_TTL_SECONDS = 14 * 24 * 60 * 60;

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final TokenBlacklistService blacklistService;
    private final IpUtil ipUtil;
    private final DeviceDetector deviceDetector;
    private final GeoIpService geoIpService;

    /**
     * SAS OAuth2AuthorizationService (SessionBridgingAuthorizationService 데코레이터).
     * 순환 의존(데코레이터 → SessionService → authorizationService) 방지를 위해
     * ObjectProvider로 지연 해결한다.
     */
    private final ObjectProvider<OAuth2AuthorizationService> authorizationServiceProvider;

    @Autowired(required = false)
    private FindByIndexNameSessionRepository<? extends Session> indexedSessionRepository;

    @Value("${session.max-per-user:5}")
    private int maxSessionsPerUser;

    @Override
    @Transactional
    public UserSession createSasSession(User user, String refreshToken, String accessTokenJti,
                                        String clientId, String authorizationId, Set<String> scopes,
                                        HttpServletRequest request) {
        // 동시 세션 수 확인 및 제한
        enforceSessionLimit(user);

        String tokenHash = hashToken(refreshToken);
        String ipAddress = "unknown";
        String userAgent = null;
        if (request != null) {
            ipAddress = ipUtil.normalizeIp(ipUtil.getClientIp(request));
            userAgent = request.getHeader("User-Agent");
        }
        DeviceDetector.DeviceInfo deviceInfo = deviceDetector.parse(userAgent);
        String location = geoIpService.resolveLocation(ipAddress);

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(SAS_REFRESH_TOKEN_TTL_SECONDS);

        String scopesStr = (scopes != null && !scopes.isEmpty()) ? String.join(" ", scopes) : null;

        UserSession session = UserSession.builder()
                .refreshTokenHash(tokenHash)
                .user(user)
                .accessTokenJti(accessTokenJti)
                .clientId(clientId)
                .authorizationId(authorizationId)
                .deviceType(deviceInfo.getDeviceType())
                .deviceName(deviceInfo.getDeviceName())
                .ipAddress(ipAddress)
                .location(location)
                .userAgent(userAgent)
                .expiresAt(expiresAt)
                .isRevoked(false)
                .pkceFlow(true)  // SAS는 모든 클라이언트에 PKCE 강제
                .scopes(scopesStr)
                .lastActiveAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        return sessionRepository.save(session);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSession> findByAuthorizationId(String authorizationId) {
        return sessionRepository.findByAuthorizationId(authorizationId);
    }

    @Override
    @Transactional
    public void revokeSessionEntity(UserSession session) {
        if (Boolean.TRUE.equals(session.getIsRevoked())) {
            return;  // 이미 무효화된 세션 (SessionService ↔ SAS 데코레이터 양방향 호출 시 중복 방지)
        }

        session.revoke();
        sessionRepository.save(session);

        // 해당 세션의 Access Token 블랙리스트 등록 (SAS access token TTL 기준)
        if (session.getAccessTokenJti() != null) {
            blacklistService.blacklistJti(session.getAccessTokenJti(), SAS_ACCESS_TOKEN_TTL_SECONDS);
        }

        log.info("Session revoked via SAS bridging: {}", session.getRefreshTokenHash());
    }

    /**
     * 세션에 연결된 SAS OAuth2Authorization을 제거해 refresh token까지 무효화한다.
     * 데코레이터의 remove()가 revokeSessionEntity()를 다시 호출하지만, 이미 무효화된 세션은 건너뛰므로 안전하다.
     */
    private void removeSasAuthorization(UserSession session) {
        if (session.getAuthorizationId() == null) {
            return;  // 레거시(JWT) 세션은 SAS authorization이 없음
        }
        try {
            OAuth2AuthorizationService authorizationService = authorizationServiceProvider.getIfAvailable();
            if (authorizationService == null) {
                return;
            }
            OAuth2Authorization authorization = authorizationService.findById(session.getAuthorizationId());
            if (authorization != null) {
                authorizationService.remove(authorization);
            }
        } catch (Exception e) {
            log.error("Failed to remove SAS authorization for session: {}", e.getMessage());
        }
    }

    /**
     * 동시 세션 수 제한 적용
     */
    private void enforceSessionLimit(User user) {
        long activeCount = sessionRepository.countActiveSessionsByUser(user, LocalDateTime.now());

        if (activeCount >= maxSessionsPerUser) {
            // 가장 오래된 세션을 무효화
            List<UserSession> oldestSessions = sessionRepository
                    .findOldestActiveSessionsByUser(user, LocalDateTime.now());

            if (!oldestSessions.isEmpty()) {
                UserSession oldest = oldestSessions.get(0);
                oldest.revoke();

                // 해당 세션의 Access Token도 블랙리스트에 추가
                if (oldest.getAccessTokenJti() != null) {
                    blacklistService.blacklistJti(
                            oldest.getAccessTokenJti(),
                            SAS_ACCESS_TOKEN_TTL_SECONDS
                    );
                }

                sessionRepository.save(oldest);
                log.info("Session limit exceeded. Revoked oldest session for user: {}", user.getEmail());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSessionDTO> getActiveSessions(String userEmail, String currentRefreshToken) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String currentHash = currentRefreshToken != null ? hashToken(currentRefreshToken) : null;

        List<UserSession> sessions = sessionRepository
                .findActiveSessionsByUser(user, LocalDateTime.now());

        return sessions.stream()
                .map(session -> toDTO(session, currentHash))
                .collect(Collectors.toList());
    }

    private UserSessionDTO toDTO(UserSession session, String currentHash) {
        boolean isCurrent = currentHash != null &&
                currentHash.equals(session.getRefreshTokenHash());

        return UserSessionDTO.builder()
                .sessionId(session.getRefreshTokenHash())
                .deviceType(session.getDeviceType())
                .deviceName(session.getDeviceName())
                .ipAddress(session.getIpAddress())
                .location(session.getLocation())
                .lastActiveAt(session.getLastActiveAt())
                .createdAt(session.getCreatedAt())
                .expiresAt(session.getExpiresAt())
                .isCurrent(isCurrent)
                .build();
    }

    @Override
    @Transactional
    public void revokeSession(String userEmail, String sessionId, String currentAccessToken) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        UserSession session = sessionRepository.findByRefreshTokenHash(sessionId)
                .orElseThrow(() -> new BadCredentialsException("Session not found"));

        // 본인의 세션인지 확인
        if (!session.getUser().getId().equals(user.getId())) {
            throw new BadCredentialsException("Cannot revoke another user's session");
        }

        session.revoke();
        sessionRepository.save(session);

        // 해당 세션의 Access Token 블랙리스트 등록
        if (session.getAccessTokenJti() != null) {
            blacklistService.blacklistJti(
                    session.getAccessTokenJti(),
                    SAS_ACCESS_TOKEN_TTL_SECONDS
            );
        }

        // SAS authorization도 함께 제거해 refresh token까지 무효화
        removeSasAuthorization(session);

        log.info("Session revoked: {} for user: {}", sessionId, userEmail);
    }

    @Override
    @Transactional
    public void revokeAllSessions(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // 모든 활성 세션의 Access Token을 블랙리스트에 추가
        List<UserSession> activeSessions = sessionRepository
                .findActiveSessionsByUser(user, LocalDateTime.now());

        for (UserSession session : activeSessions) {
            if (session.getAccessTokenJti() != null) {
                blacklistService.blacklistJti(
                        session.getAccessTokenJti(),
                        SAS_ACCESS_TOKEN_TTL_SECONDS
                );
            }
        }

        int revokedCount = sessionRepository.revokeAllByUser(user);
        log.info("All sessions revoked for user: {}. Count: {}", userEmail, revokedCount);

        // SAS authorization도 함께 제거해 refresh token까지 무효화
        for (UserSession session : activeSessions) {
            removeSasAuthorization(session);
        }

        // OAuth 서버사이드 세션(Redis)도 무효화
        revokeAllOAuthSessions(userEmail);
    }

    @Override
    public void revokeAllOAuthSessions(String userEmail) {
        if (indexedSessionRepository == null) {
            log.warn("IndexedSessionRepository is not available, skipping OAuth session revocation");
            return;
        }

        var sessions = indexedSessionRepository.findByPrincipalName(userEmail);
        if (sessions != null && !sessions.isEmpty()) {
            sessions.forEach((id, session) -> {
                indexedSessionRepository.deleteById(id);
                log.info("Revoked OAuth server-side session: {} for user: {}", id, userEmail);
            });
        }
    }

    @Override
    @Transactional
    public void revokeOtherSessions(String userEmail, String currentRefreshToken) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String currentHash = hashToken(currentRefreshToken);

        // 현재 세션 제외 다른 세션의 Access Token을 블랙리스트에 추가
        List<UserSession> activeSessions = sessionRepository
                .findActiveSessionsByUser(user, LocalDateTime.now());

        for (UserSession session : activeSessions) {
            if (!session.getRefreshTokenHash().equals(currentHash) &&
                    session.getAccessTokenJti() != null) {
                blacklistService.blacklistJti(
                        session.getAccessTokenJti(),
                        SAS_ACCESS_TOKEN_TTL_SECONDS
                );
            }
        }

        int revokedCount = sessionRepository.revokeOthersByUser(user, currentHash);
        log.info("Other sessions revoked for user: {}. Count: {}", userEmail, revokedCount);

        // 현재 세션 제외 SAS authorization도 함께 제거해 refresh token까지 무효화
        for (UserSession session : activeSessions) {
            if (!session.getRefreshTokenHash().equals(currentHash)) {
                removeSasAuthorization(session);
            }
        }
    }

    @Override
    public String hashToken(String token) {
        return DigestUtils.sha256Hex(token);
    }
}
