package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.dto.UserSessionDTO;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.UserSession;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.repository.UserSessionRepository;
import kr.hyfata.rest.api.service.impl.SessionServiceImpl;
import kr.hyfata.rest.api.util.DeviceDetector;
import kr.hyfata.rest.api.util.GeoIpService;
import kr.hyfata.rest.api.util.IpUtil;
import kr.hyfata.rest.api.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private JwtUtil jwtUtil;

    @InjectMocks
    private SessionServiceImpl sessionService;

    private User testUser;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionService, "maxSessionsPerUser", 5);
        ReflectionTestUtils.setField(sessionService, "refreshTokenExpiration", 1209600000L);

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .build();

        mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0");
    }

    @Test
    @DisplayName("세션 생성 성공")
    void createSession_success() {
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
        UserSession result = sessionService.createSession(testUser, refreshToken, accessTokenJti, mockRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getDeviceType()).isEqualTo("Desktop");
        assertThat(result.getDeviceName()).isEqualTo("Chrome on Windows");
        assertThat(result.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(result.getLocation()).isEqualTo("Seoul, South Korea");
        assertThat(result.getIsRevoked()).isFalse();

        verify(sessionRepository).save(any(UserSession.class));
    }

    @Test
    @DisplayName("세션 검증 - 유효한 세션")
    void validateSession_validSession() {
        // given
        String refreshToken = "valid-token";
        UserSession validSession = UserSession.builder()
                .refreshTokenHash(sessionService.hashToken(refreshToken))
                .user(testUser)
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(sessionRepository.findByRefreshTokenHash(any())).thenReturn(Optional.of(validSession));

        // when
        boolean result = sessionService.validateSession(refreshToken);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("세션 검증 - 무효화된 세션")
    void validateSession_revokedSession() {
        // given
        String refreshToken = "revoked-token";
        UserSession revokedSession = UserSession.builder()
                .refreshTokenHash(sessionService.hashToken(refreshToken))
                .user(testUser)
                .isRevoked(true)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(sessionRepository.findByRefreshTokenHash(any())).thenReturn(Optional.of(revokedSession));

        // when
        boolean result = sessionService.validateSession(refreshToken);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("세션 검증 - 만료된 세션")
    void validateSession_expiredSession() {
        // given
        String refreshToken = "expired-token";
        UserSession expiredSession = UserSession.builder()
                .refreshTokenHash(sessionService.hashToken(refreshToken))
                .user(testUser)
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(sessionRepository.findByRefreshTokenHash(any())).thenReturn(Optional.of(expiredSession));

        // when
        boolean result = sessionService.validateSession(refreshToken);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("세션 검증 - 존재하지 않는 세션")
    void validateSession_notFound() {
        // given
        String refreshToken = "nonexistent-token";
        when(sessionRepository.findByRefreshTokenHash(any())).thenReturn(Optional.empty());

        // when
        boolean result = sessionService.validateSession(refreshToken);

        // then
        assertThat(result).isFalse();
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

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
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
    void createSession_sessionLimitExceeded_revokesOldest() {
        // given
        String refreshToken = "new-token";
        String accessTokenJti = "new-jti";

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
        when(jwtUtil.getJwtExpiration()).thenReturn(900000L);
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(i -> i.getArgument(0));

        // when
        sessionService.createSession(testUser, refreshToken, accessTokenJti, mockRequest);

        // then
        assertThat(oldestSession.getIsRevoked()).isTrue();
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
}
