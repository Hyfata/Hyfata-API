package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.auth.dto.UserSessionDTO;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.entity.UserSession;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.repository.UserSessionRepository;
import kr.hyfata.rest.api.auth.service.impl.SessionServiceImpl;
import kr.hyfata.rest.api.common.util.DeviceDetector;
import kr.hyfata.rest.api.common.util.GeoIpService;
import kr.hyfata.rest.api.common.util.IpUtil;
import kr.hyfata.rest.api.auth.service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenBlacklistService blacklistService;

    @Mock
    private IpUtil ipUtil;

    @Mock
    private DeviceDetector deviceDetector;

    @Mock
    private GeoIpService geoIpService;

    @Mock
    private ObjectProvider<OAuth2AuthorizationService> authorizationServiceProvider;

    @InjectMocks
    private SessionServiceImpl sessionService;

    private User testUser;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionService, "maxSessionsPerUser", 5);

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .build();

        mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0");
    }

    @Test
    @DisplayName("SAS 세션 생성 성공")
    void createSasSession_success() {
        // given
        String refreshToken = "test-refresh-token";
        String accessTokenJti = "test-jti";

        when(ipUtil.getClientIp(mockRequest)).thenReturn("192.168.1.100");
        when(ipUtil.normalizeIp("192.168.1.100")).thenReturn("192.168.1.100");
        when(deviceDetector.parse(any())).thenReturn(
                DeviceDetector.DeviceInfo.builder()
                        .deviceType("Desktop")
                        .deviceName("Chrome on Windows")
                        .build()
        );
        when(geoIpService.resolveLocation("192.168.1.100")).thenReturn("Seoul, South Korea");
        when(sessionRepository.countActiveSessionsByUser(any(), any())).thenReturn(0L);
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserSession result = sessionService.createSasSession(testUser, refreshToken, accessTokenJti,
                "test-client", "test-authorization-id", null, mockRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getDeviceType()).isEqualTo("Desktop");
        assertThat(result.getDeviceName()).isEqualTo("Chrome on Windows");
        assertThat(result.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(result.getLocation()).isEqualTo("Seoul, South Korea");
        assertThat(result.getIsRevoked()).isFalse();
        assertThat(result.getClientId()).isEqualTo("test-client");
        assertThat(result.getAuthorizationId()).isEqualTo("test-authorization-id");
        assertThat(result.getPkceFlow()).isTrue();  // SAS는 PKCE 강제

        verify(sessionRepository).save(any(UserSession.class));
    }

    @Test
    @DisplayName("SAS 세션 생성 - 요청 컨텍스트 없이도 안전하게 생성")
    void createSasSession_nullRequest_createsSessionSafely() {
        // given
        when(deviceDetector.parse(null)).thenReturn(
                DeviceDetector.DeviceInfo.builder()
                        .deviceType("Unknown")
                        .deviceName("Unknown Device")
                        .build()
        );
        when(sessionRepository.countActiveSessionsByUser(any(), any())).thenReturn(0L);
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserSession result = sessionService.createSasSession(testUser, "token", "jti",
                "client", "auth-id", null, null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getIpAddress()).isEqualTo("unknown");
        assertThat(result.getDeviceType()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("활성 세션 목록 조회")
    void getActiveSessions_success() {
        // given
        String currentRefreshToken = "current-token";
        UserSession session1 = UserSession.builder()
                .refreshTokenHash(sessionService.hashToken(currentRefreshToken))
                .user(testUser)
                .deviceType("Desktop")
                .deviceName("Chrome on Windows")
                .ipAddress("192.168.1.100")
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .lastActiveAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        UserSession session2 = UserSession.builder()
                .refreshTokenHash(sessionService.hashToken("other-token"))
                .user(testUser)
                .deviceType("Mobile")
                .deviceName("Safari on iPhone")
                .ipAddress("192.168.1.105")
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().plusDays(5))
                .lastActiveAt(LocalDateTime.now().minusHours(2))
                .createdAt(LocalDateTime.now().minusDays(3))
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(java.util.Optional.of(testUser));
        when(sessionRepository.findActiveSessionsByUser(any(), any())).thenReturn(List.of(session1, session2));

        // when
        List<UserSessionDTO> result = sessionService.getActiveSessions("test@example.com", currentRefreshToken);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getIsCurrent()).isTrue();
        assertThat(result.get(1).getIsCurrent()).isFalse();
    }

    @Test
    @DisplayName("동시 세션 제한 - 최대 5개 초과 시 가장 오래된 세션 무효화")
    void createSasSession_sessionLimitExceeded_revokesOldest() {
        // given
        UserSession oldestSession = UserSession.builder()
                .refreshTokenHash("oldest-hash")
                .user(testUser)
                .accessTokenJti("old-jti")
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now().minusDays(10))
                .build();

        when(ipUtil.getClientIp(mockRequest)).thenReturn("192.168.1.100");
        when(ipUtil.normalizeIp("192.168.1.100")).thenReturn("192.168.1.100");
        when(deviceDetector.parse(any())).thenReturn(
                DeviceDetector.DeviceInfo.builder()
                        .deviceType("Desktop")
                        .deviceName("Chrome on Windows")
                        .build()
        );
        when(sessionRepository.countActiveSessionsByUser(any(), any())).thenReturn(5L);
        when(sessionRepository.findOldestActiveSessionsByUser(any(), any())).thenReturn(List.of(oldestSession));
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(i -> i.getArgument(0));

        // when
        sessionService.createSasSession(testUser, "new-token", "new-jti",
                "client", "auth-id", null, mockRequest);

        // then
        assertThat(oldestSession.getIsRevoked()).isTrue();
        // SAS access token TTL(900초)로 블랙리스트 등록
        verify(blacklistService).blacklistJti("old-jti", 900L);
        verify(sessionRepository, times(2)).save(any(UserSession.class));
    }

    @Test
    @DisplayName("토큰 해시 생성")
    void hashToken_success() {
        // given
        String token = "test-token";

        // when
        String hash1 = sessionService.hashToken(token);
        String hash2 = sessionService.hashToken(token);

        // then
        assertThat(hash1).isNotNull();
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex characters
        assertThat(hash1).isEqualTo(hash2); // Same input produces same hash
    }

    @Test
    @DisplayName("SAS 세션 생성 - scope 포함")
    void createSasSession_withScopes_savesScopes() {
        // given
        Set<String> scopes = Set.of("profile", "email", "account:manage");

        when(ipUtil.getClientIp(mockRequest)).thenReturn("192.168.1.100");
        when(ipUtil.normalizeIp("192.168.1.100")).thenReturn("192.168.1.100");
        when(deviceDetector.parse(any())).thenReturn(
                DeviceDetector.DeviceInfo.builder()
                        .deviceType("Desktop")
                        .deviceName("Chrome on Windows")
                        .build()
        );
        when(sessionRepository.countActiveSessionsByUser(any(), any())).thenReturn(0L);
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserSession result = sessionService.createSasSession(testUser, "token", "jti",
                "client", "auth-id", scopes, mockRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getScopes().split(" ")).containsExactlyInAnyOrder("account:manage", "email", "profile");
    }

    @Test
    @DisplayName("SAS 세션 생성 - scope가 null이면 scopes 필드가 null")
    void createSasSession_withNullScopes_savesNullScopes() {
        // given
        when(ipUtil.getClientIp(mockRequest)).thenReturn("192.168.1.100");
        when(ipUtil.normalizeIp("192.168.1.100")).thenReturn("192.168.1.100");
        when(deviceDetector.parse(any())).thenReturn(
                DeviceDetector.DeviceInfo.builder()
                        .deviceType("Desktop")
                        .deviceName("Chrome on Windows")
                        .build()
        );
        when(sessionRepository.countActiveSessionsByUser(any(), any())).thenReturn(0L);
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserSession result = sessionService.createSasSession(testUser, "token", "jti",
                "client", "auth-id", null, mockRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getScopes()).isNull();
    }
}
