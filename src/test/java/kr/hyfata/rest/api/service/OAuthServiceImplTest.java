package kr.hyfata.rest.api.service;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.auth.dto.OAuthTokenResponse;
import kr.hyfata.rest.api.auth.entity.AuthorizationCode;
import kr.hyfata.rest.api.auth.entity.Client;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.entity.UserSession;
import kr.hyfata.rest.api.auth.repository.AuthorizationCodeRepository;
import kr.hyfata.rest.api.auth.repository.ClientRepository;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.repository.UserSessionRepository;
import kr.hyfata.rest.api.auth.service.SessionService;
import kr.hyfata.rest.api.auth.service.TokenBlacklistService;
import kr.hyfata.rest.api.auth.service.impl.OAuthServiceImpl;
import kr.hyfata.rest.api.common.util.JwtUtil;
import kr.hyfata.rest.api.common.util.PkceUtil;
import kr.hyfata.rest.api.common.util.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthServiceImplTest {

    @Mock
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private PkceUtil pkceUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessionService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private OAuthServiceImpl oAuthService;

    private User testUser;
    private Client testClient;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .password("encoded_password")
                .enabled(true)
                .emailVerified(true)
                .build();

        testClient = Client.builder()
                .id(1L)
                .clientId("client_001")
                .clientSecret("encoded_secret")
                .name("Test Client")
                .redirectUris("https://example.com/callback")
                .enabled(true)
                .defaultScopes("profile email")
                .allowedScopes("profile email profile:write account:password account:manage 2fa:manage sessions:manage")
                .build();

        mockRequest = new MockHttpServletRequest();
    }

    @Test
    @DisplayName("Authorization Code 교환 - scope가 포함된 토큰 발급")
    void exchangeCodeForToken_withScopes_returnsTokenWithScopes() {
        // given
        String code = "auth_code_123";
        String clientId = "client_001";
        String redirectUri = "https://example.com/callback";
        String codeVerifier = "verifier_123";

        AuthorizationCode authCode = AuthorizationCode.builder()
                .code(code)
                .clientId(clientId)
                .email("test@example.com")
                .redirectUri(redirectUri)
                .scopes("profile email account:manage")
                .codeChallenge("challenge_123")
                .codeChallengeMethod("S256")
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(authorizationCodeRepository.findByCodeAndClientId(code, clientId))
                .thenReturn(Optional.of(authCode));
        when(pkceUtil.isValidCodeVerifier(codeVerifier)).thenReturn(true);
        when(pkceUtil.verifyCodeChallenge(codeVerifier, "challenge_123")).thenReturn(true);
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(testClient));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateAccessTokenWithJti(testUser, clientId, Set.of("profile", "email", "account:manage")))
                .thenReturn(new JwtUtil.TokenResult("access_token_with_scope", "jti_123"));
        when(jwtUtil.generateRefreshToken(testUser)).thenReturn("refresh_token");

        // when
        OAuthTokenResponse response = oAuthService.exchangeCodeForToken(
                code, clientId, null, redirectUri, codeVerifier, mockRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access_token_with_scope");
        assertThat(response.getScope().split(" ")).containsExactlyInAnyOrder("profile", "email", "account:manage");

        verify(sessionService).createSession(eq(testUser), eq("refresh_token"), eq("jti_123"),
                eq(mockRequest), eq(true), eq(Set.of("profile", "email", "account:manage")));
    }

    @Test
    @DisplayName("Authorization Code 교환 - scope 미지정 시 기본 scope 사용")
    void exchangeCodeForToken_withoutScopes_returnsTokenWithDefaultScopes() {
        // given
        String code = "auth_code_123";
        String clientId = "client_001";
        String redirectUri = "https://example.com/callback";
        String clientSecret = "secret";

        AuthorizationCode authCode = AuthorizationCode.builder()
                .code(code)
                .clientId(clientId)
                .email("test@example.com")
                .redirectUri(redirectUri)
                .scopes(null)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(authorizationCodeRepository.findByCodeAndClientId(code, clientId))
                .thenReturn(Optional.of(authCode));
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(testClient));
        when(passwordEncoder.matches(clientSecret, testClient.getClientSecret())).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateAccessTokenWithJti(testUser, clientId, Set.of("profile", "email")))
                .thenReturn(new JwtUtil.TokenResult("access_token_default", "jti_default"));
        when(jwtUtil.generateRefreshToken(testUser)).thenReturn("refresh_token");

        // when
        OAuthTokenResponse response = oAuthService.exchangeCodeForToken(
                code, clientId, clientSecret, redirectUri, null, mockRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getScope().split(" ")).containsExactlyInAnyOrder("profile", "email");

        verify(sessionService).createSession(eq(testUser), eq("refresh_token"), eq("jti_default"),
                eq(mockRequest), eq(false), eq(Set.of("profile", "email")));
    }

    @Test
    @DisplayName("Refresh Token 갱신 - 기존 세션의 scope 유지")
    void refreshAccessToken_preservesExistingScopes() {
        // given
        String refreshToken = "refresh_token_123";
        String clientId = "client_001";
        String clientSecret = "secret";
        String oldSessionHash = "old_hash";

        UserSession oldSession = UserSession.builder()
                .refreshTokenHash(oldSessionHash)
                .user(testUser)
                .accessTokenJti("old_jti")
                .scopes("profile email 2fa:manage")
                .pkceFlow(false)
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(jwtUtil.validateToken(refreshToken)).thenReturn(true);
        when(sessionService.validateSession(refreshToken)).thenReturn(true);
        when(sessionService.hashToken(refreshToken)).thenReturn(oldSessionHash);
        when(userSessionRepository.findById(oldSessionHash)).thenReturn(Optional.of(oldSession));
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(testClient));
        when(passwordEncoder.matches(clientSecret, testClient.getClientSecret())).thenReturn(true);
        when(jwtUtil.extractEmail(refreshToken)).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateAccessTokenWithJti(testUser, clientId, Set.of("profile", "email", "2fa:manage")))
                .thenReturn(new JwtUtil.TokenResult("new_access_token", "new_jti"));
        when(jwtUtil.generateRefreshToken(testUser)).thenReturn("new_refresh_token");

        // when
        OAuthTokenResponse response = oAuthService.refreshAccessToken(
                refreshToken, clientId, clientSecret, mockRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getScope().split(" ")).containsExactlyInAnyOrder("profile", "email", "2fa:manage");

        verify(sessionService).createSession(eq(testUser), eq("new_refresh_token"), eq("new_jti"),
                eq(mockRequest), eq(false), eq(Set.of("profile", "email", "2fa:manage")));
    }

    @Test
    @DisplayName("Refresh Token 갱신 - 기존 세션에 scope 없으면 기본 scope 사용")
    void refreshAccessToken_withoutExistingScopes_usesDefaultScopes() {
        // given
        String refreshToken = "refresh_token_123";
        String clientId = "client_001";
        String clientSecret = "secret";
        String oldSessionHash = "old_hash";

        UserSession oldSession = UserSession.builder()
                .refreshTokenHash(oldSessionHash)
                .user(testUser)
                .accessTokenJti("old_jti")
                .scopes(null)
                .pkceFlow(true)
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(jwtUtil.validateToken(refreshToken)).thenReturn(true);
        when(sessionService.validateSession(refreshToken)).thenReturn(true);
        when(sessionService.hashToken(refreshToken)).thenReturn(oldSessionHash);
        when(userSessionRepository.findById(oldSessionHash)).thenReturn(Optional.of(oldSession));
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(testClient));
        when(jwtUtil.extractEmail(refreshToken)).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateAccessTokenWithJti(testUser, clientId, Set.of("profile", "email")))
                .thenReturn(new JwtUtil.TokenResult("new_access_token", "new_jti"));
        when(jwtUtil.generateRefreshToken(testUser)).thenReturn("new_refresh_token");

        // when
        OAuthTokenResponse response = oAuthService.refreshAccessToken(
                refreshToken, clientId, null, mockRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getScope().split(" ")).containsExactlyInAnyOrder("profile", "email");

        verify(sessionService).createSession(eq(testUser), eq("new_refresh_token"), eq("new_jti"),
                eq(mockRequest), eq(true), eq(Set.of("profile", "email")));
    }

    @Test
    @DisplayName("Authorization Code 생성 - scope 저장")
    void generateAuthorizationCode_withScopes_savesScopes() {
        // given
        String clientId = "client_001";
        String email = "test@example.com";
        String redirectUri = "https://example.com/callback";
        String state = "state_123";
        String scopes = "profile email account:manage";

        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(testClient));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(tokenGenerator.generatePasswordResetToken()).thenReturn("generated_code");
        when(authorizationCodeRepository.save(any(AuthorizationCode.class)))
                .thenAnswer(i -> i.getArgument(0));

        // when
        String code = oAuthService.generateAuthorizationCode(clientId, email, redirectUri, state, scopes);

        // then
        assertThat(code).isEqualTo("generated_code");
        verify(authorizationCodeRepository).save(argThat(authCode ->
                authCode.getScopes().equals(scopes)
        ));
    }
}
