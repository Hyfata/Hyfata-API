package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.session.entity.LoginHistory;
import kr.hyfata.rest.api.user.User;
import kr.hyfata.rest.api.session.entity.UserSession;
import kr.hyfata.rest.api.session.repository.LoginHistoryRepository;
import kr.hyfata.rest.api.user.UserRepository;
import kr.hyfata.rest.api.session.service.SessionService;
import kr.hyfata.rest.api.oauth.SessionBridgingAuthorizationService;
import kr.hyfata.rest.api.infrastructure.util.DeviceDetector;
import kr.hyfata.rest.api.infrastructure.util.GeoIpService;
import kr.hyfata.rest.api.infrastructure.util.IpUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionBridgingAuthorizationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private IpUtil ipUtil;

    @Mock
    private DeviceDetector deviceDetector;

    @Mock
    private GeoIpService geoIpService;

    private SessionBridgingAuthorizationService service;

    private User testUser;
    private RegisteredClient registeredClient;

    @BeforeEach
    void setUp() {
        service = new SessionBridgingAuthorizationService(jdbcTemplate, registeredClientRepository,
                sessionService, userRepository, loginHistoryRepository, ipUtil, deviceDetector, geoIpService);

        testUser = User.builder().id(1L).email("user@example.com").username("testuser").build();

        registeredClient = RegisteredClient.withId("test-client")
                .clientId("test-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.example.com/callback")
                .build();

        // 실제 SHA-256 해시 사용 (로테이션/재저장 구분 검증용)
        when(sessionService.hashToken(anyString())).thenAnswer(inv -> DigestUtils.sha256Hex((String) inv.getArgument(0)));
        when(deviceDetector.parse(any())).thenReturn(
                DeviceDetector.DeviceInfo.builder().deviceType("Unknown").deviceName("Unknown Device").build());
    }

    private OAuth2Authorization authorizationWithTokens(String authorizationId, String refreshTokenValue, String jti) {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-token-" + jti, Instant.now(), Instant.now().plusSeconds(900), Set.of("profile", "email"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(refreshTokenValue, Instant.now());

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(authorizationId)
                .principalName("user@example.com")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("profile", "email"))
                .refreshToken(refreshToken);
        if (jti != null) {
            builder.token(accessToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, Map.of("jti", jti)));
        }
        return builder.build();
    }

    @Test
    @DisplayName("최초 토큰 발급 save - 세션 생성 + LoginHistory 기록")
    void save_initialIssuance_createsSessionAndLoginHistory() {
        // given
        OAuth2Authorization authorization = authorizationWithTokens("auth-1", "refresh-token-1", "jti-1");

        when(sessionService.findByAuthorizationId("auth-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        // when (RequestContextHolder 없는 환경 — request는 null로 전달되어야 함)
        service.save(authorization);

        // then
        verify(sessionService).createSasSession(eq(testUser), eq("refresh-token-1"), eq("jti-1"),
                eq("test-client"), eq("auth-1"), eq(Set.of("profile", "email")), isNull());
        verify(loginHistoryRepository).save(any(LoginHistory.class));
        verify(sessionService, never()).revokeSessionEntity(any());
    }

    @Test
    @DisplayName("동일 refresh token 재저장(해시 동일) - 아무 작업 안 함")
    void save_sameRefreshToken_skips() {
        // given
        OAuth2Authorization authorization = authorizationWithTokens("auth-1", "refresh-token-1", "jti-1");
        UserSession existing = UserSession.builder()
                .refreshTokenHash(DigestUtils.sha256Hex("refresh-token-1"))
                .user(testUser)
                .authorizationId("auth-1")
                .build();

        when(sessionService.findByAuthorizationId("auth-1")).thenReturn(Optional.of(existing));

        // when
        service.save(authorization);

        // then
        verify(sessionService, never()).createSasSession(any(), any(), any(), any(), any(), any(), any());
        verify(sessionService, never()).revokeSessionEntity(any());
        verify(loginHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("refresh token 로테이션(해시 변경) - 구 세션 무효화 + 신규 세션 생성, LoginHistory 없음")
    void save_rotation_replacesSession() {
        // given
        OAuth2Authorization authorization = authorizationWithTokens("auth-1", "refresh-token-2", "jti-2");
        UserSession oldSession = UserSession.builder()
                .refreshTokenHash(DigestUtils.sha256Hex("refresh-token-1"))
                .user(testUser)
                .authorizationId("auth-1")
                .accessTokenJti("jti-1")
                .build();

        when(sessionService.findByAuthorizationId("auth-1")).thenReturn(Optional.of(oldSession));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        // when
        service.save(authorization);

        // then
        verify(sessionService).revokeSessionEntity(oldSession);
        verify(sessionService).createSasSession(eq(testUser), eq("refresh-token-2"), eq("jti-2"),
                eq("test-client"), eq("auth-1"), eq(Set.of("profile", "email")), isNull());
        verify(loginHistoryRepository, never()).save(any());  // 로테이션은 로그인 이력 아님
    }

    @Test
    @DisplayName("refresh token 미포함 save (authorization code 단계) - 아무 작업 안 함")
    void save_withoutRefreshToken_skips() {
        // given
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id("auth-1")
                .principalName("user@example.com")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("profile", "email"))
                .build();

        // when
        service.save(authorization);

        // then
        verify(sessionService, never()).findByAuthorizationId(any());
        verify(sessionService, never()).createSasSession(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 세션 생성 안 함")
    void save_userNotFound_skips() {
        // given
        OAuth2Authorization authorization = authorizationWithTokens("auth-1", "refresh-token-1", "jti-1");

        when(sessionService.findByAuthorizationId("auth-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        // when
        service.save(authorization);

        // then
        verify(sessionService, never()).createSasSession(any(), any(), any(), any(), any(), any(), any());
        verify(loginHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("remove - 연결된 세션 무효화 (JTI 블랙리스트는 revokeSessionEntity가 처리)")
    void remove_revokesSession() {
        // given
        OAuth2Authorization authorization = authorizationWithTokens("auth-1", "refresh-token-1", "jti-1");
        UserSession session = UserSession.builder()
                .refreshTokenHash(DigestUtils.sha256Hex("refresh-token-1"))
                .user(testUser)
                .authorizationId("auth-1")
                .accessTokenJti("jti-1")
                .build();

        when(sessionService.findByAuthorizationId("auth-1")).thenReturn(Optional.of(session));

        // when
        service.remove(authorization);

        // then
        verify(sessionService).revokeSessionEntity(session);
    }

    @Test
    @DisplayName("remove - 연결된 세션이 없으면 아무 작업 안 함")
    void remove_noSession_skips() {
        // given
        OAuth2Authorization authorization = authorizationWithTokens("auth-1", "refresh-token-1", "jti-1");
        when(sessionService.findByAuthorizationId("auth-1")).thenReturn(Optional.empty());

        // when
        service.remove(authorization);

        // then
        verify(sessionService, never()).revokeSessionEntity(any());
    }

    @Test
    @DisplayName("브리징 예외는 위임 동작을 막지 않음 (로그만)")
    void save_bridgingFailure_doesNotPropagate() {
        // given
        OAuth2Authorization authorization = authorizationWithTokens("auth-1", "refresh-token-1", "jti-1");
        when(sessionService.findByAuthorizationId("auth-1")).thenThrow(new RuntimeException("DB error"));

        // when (예외가 전파되지 않아야 함)
        service.save(authorization);

        // then
        verify(sessionService).findByAuthorizationId("auth-1");
    }
}
