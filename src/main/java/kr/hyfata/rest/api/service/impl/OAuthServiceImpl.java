package kr.hyfata.rest.api.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.dto.OAuthTokenResponse;
import kr.hyfata.rest.api.entity.AuthorizationCode;
import kr.hyfata.rest.api.entity.Client;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.UserSession;
import kr.hyfata.rest.api.repository.AuthorizationCodeRepository;
import kr.hyfata.rest.api.repository.ClientRepository;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.repository.UserSessionRepository;
import kr.hyfata.rest.api.service.OAuthService;
import kr.hyfata.rest.api.service.SessionService;
import kr.hyfata.rest.api.service.TokenBlacklistService;
import kr.hyfata.rest.api.util.JwtUtil;
import kr.hyfata.rest.api.util.PkceUtil;
import kr.hyfata.rest.api.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthServiceImpl implements OAuthService {

    private final AuthorizationCodeRepository authorizationCodeRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtUtil jwtUtil;
    private final TokenGenerator tokenGenerator;
    private final PkceUtil pkceUtil;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public String generateAuthorizationCode(String clientId, String email, String redirectUri, String state) {
        return generateAuthorizationCode(clientId, email, redirectUri, state, null, null);
    }

    @Override
    public String generateAuthorizationCode(String clientId, String email, String redirectUri, String state,
                                           String codeChallenge, String codeChallengeMethod) {
        // 클라이언트 검증
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new BadCredentialsException("Invalid client"));

        if (!client.getEnabled()) {
            throw new BadCredentialsException("Client is disabled");
        }

        // Redirect URI 검증
        if (!validateRedirectUri(clientId, redirectUri)) {
            throw new BadCredentialsException("Invalid redirect URI");
        }

        // 사용자 검증
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Authorization Code 생성
        String code = tokenGenerator.generatePasswordResetToken();  // 긴 난수 토큰

        AuthorizationCode.AuthorizationCodeBuilder builder = AuthorizationCode.builder()
                .code(code)
                .clientId(clientId)
                .email(email)
                .redirectUri(redirectUri)
                .state(state)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(10));  // 10분 유효

        // PKCE 파라미터가 제공되면 저장
        if (codeChallenge != null && !codeChallenge.isEmpty()) {
            builder.codeChallenge(codeChallenge);
            builder.codeChallengeMethod(codeChallengeMethod != null ? codeChallengeMethod : "S256");
            log.info("PKCE enabled for authorization code: client_id={}, method={}", clientId, codeChallengeMethod);
        }

        AuthorizationCode authCode = builder.build();
        authorizationCodeRepository.save(authCode);

        if (codeChallenge != null && !codeChallenge.isEmpty()) {
            log.info("Authorization code generated with PKCE: client_id={}, email={}", clientId, email);
        } else {
            log.info("Authorization code generated for client: {}, email: {}", clientId, email);
        }

        return code;
    }

    @Override
    public OAuthTokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri) {
        return exchangeCodeForToken(code, clientId, clientSecret, redirectUri, null);
    }

    @Override
    public OAuthTokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri, String codeVerifier) {
        // 1. Authorization Code 검증
        AuthorizationCode authCode = authorizationCodeRepository.findByCodeAndClientId(code, clientId)
                .orElseThrow(() -> new BadCredentialsException("Invalid authorization code"));

        // 2. 코드 만료 여부 확인
        if (LocalDateTime.now().isAfter(authCode.getExpiresAt())) {
            authorizationCodeRepository.delete(authCode);
            throw new BadCredentialsException("Authorization code expired");
        }

        // 3. 코드 사용 여부 확인 (한 번만 사용 가능)
        if (authCode.getUsed()) {
            throw new BadCredentialsException("Authorization code already used");
        }

        // 4. Redirect URI 검증
        if (!authCode.getRedirectUri().equals(redirectUri)) {
            throw new BadCredentialsException("Redirect URI mismatch");
        }

        // 5. PKCE 검증 (code_challenge가 저장되어 있으면 code_verifier 필수)
        if (authCode.getCodeChallenge() != null && !authCode.getCodeChallenge().isEmpty()) {
            if (codeVerifier == null || codeVerifier.isEmpty()) {
                throw new BadCredentialsException("code_verifier is required for PKCE flow");
            }

            // code_verifier 유효성 검증
            if (!pkceUtil.isValidCodeVerifier(codeVerifier)) {
                throw new BadCredentialsException("Invalid code_verifier format");
            }

            // code_verifier와 code_challenge 검증
            if (!pkceUtil.verifyCodeChallenge(codeVerifier, authCode.getCodeChallenge())) {
                log.warn("PKCE verification failed: clientId={}, email={}", clientId, authCode.getEmail());
                throw new BadCredentialsException("code_verifier verification failed");
            }

            log.debug("PKCE verification successful: clientId={}, email={}", clientId, authCode.getEmail());
        }

        // 6. Client Secret 검증
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new BadCredentialsException("Invalid client credentials"));

        // BCrypt로 저장된 clientSecret과 비교
        if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
            throw new BadCredentialsException("Invalid client credentials");
        }

        if (!client.getEnabled()) {
            throw new BadCredentialsException("Client is disabled");
        }

        // 7. 사용자 조회
        User user = userRepository.findByEmail(authCode.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // 8. 코드 사용 표시
        authCode.setUsed(true);
        authorizationCodeRepository.save(authCode);

        // 9. 토큰 생성
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        long expiresIn = 86400000;  // 24시간

        if (authCode.getCodeChallenge() != null && !authCode.getCodeChallenge().isEmpty()) {
            log.info("Authorization code exchanged for tokens (PKCE): clientId={}, email={}", clientId, authCode.getEmail());
        } else {
            log.info("Authorization code exchanged for tokens: clientId={}, email={}", clientId, authCode.getEmail());
        }

        return OAuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .scope("user:email user:profile")
                .build();
    }

    @Override
    public boolean validateAuthorizationCode(String code, String clientId) {
        return authorizationCodeRepository.findByCodeAndClientId(code, clientId)
                .map(authCode -> {
                    // 만료 여부 확인
                    if (LocalDateTime.now().isAfter(authCode.getExpiresAt())) {
                        return false;
                    }
                    // 사용 여부 확인
                    return !authCode.getUsed();
                })
                .orElse(false);
    }

    @Override
    public boolean validateRedirectUri(String clientId, String redirectUri) {
        return clientRepository.findByClientId(clientId)
                .map(client -> {
                    String[] redirectUris = client.getRedirectUris().split(",");
                    return Arrays.asList(redirectUris).contains(redirectUri);
                })
                .orElse(false);
    }

    @Override
    public boolean validateState(String code, String state) {
        return authorizationCodeRepository.findByCode(code)
                .map(authCode -> authCode.getState() != null && authCode.getState().equals(state))
                .orElse(false);
    }

    @Override
    public OAuthTokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret,
                                                   String redirectUri, String codeVerifier, HttpServletRequest request) {
        // 1. Authorization Code 검증
        AuthorizationCode authCode = authorizationCodeRepository.findByCodeAndClientId(code, clientId)
                .orElseThrow(() -> new BadCredentialsException("Invalid authorization code"));

        // 2. 코드 만료 여부 확인
        if (LocalDateTime.now().isAfter(authCode.getExpiresAt())) {
            authorizationCodeRepository.delete(authCode);
            throw new BadCredentialsException("Authorization code expired");
        }

        // 3. 코드 사용 여부 확인 (한 번만 사용 가능)
        if (authCode.getUsed()) {
            throw new BadCredentialsException("Authorization code already used");
        }

        // 4. Redirect URI 검증
        if (!authCode.getRedirectUri().equals(redirectUri)) {
            throw new BadCredentialsException("Redirect URI mismatch");
        }

        // 5. PKCE 또는 Client Secret 검증
        boolean isPkceFlow = authCode.getCodeChallenge() != null && !authCode.getCodeChallenge().isEmpty();

        if (isPkceFlow) {
            // Public Client (PKCE Flow) - code_verifier로 검증, client_secret 불필요
            if (codeVerifier == null || codeVerifier.isEmpty()) {
                throw new BadCredentialsException("code_verifier is required for PKCE flow");
            }

            // code_verifier 유효성 검증
            if (!pkceUtil.isValidCodeVerifier(codeVerifier)) {
                throw new BadCredentialsException("Invalid code_verifier format");
            }

            // code_verifier와 code_challenge 검증
            if (!pkceUtil.verifyCodeChallenge(codeVerifier, authCode.getCodeChallenge())) {
                log.warn("PKCE verification failed: clientId={}, email={}", clientId, authCode.getEmail());
                throw new BadCredentialsException("code_verifier verification failed");
            }

            log.debug("PKCE verification successful: clientId={}, email={}", clientId, authCode.getEmail());
        } else {
            // Confidential Client - client_secret으로 검증
            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new BadCredentialsException("client_secret is required for non-PKCE flow");
            }
        }

        // 6. Client 검증
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new BadCredentialsException("Invalid client"));

        // Confidential Client인 경우 client_secret 검증
        if (!isPkceFlow) {
            if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
                throw new BadCredentialsException("Invalid client credentials");
            }
        }

        if (!client.getEnabled()) {
            throw new BadCredentialsException("Client is disabled");
        }

        // 7. 사용자 조회
        User user = userRepository.findByEmail(authCode.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // 8. 코드 사용 표시
        authCode.setUsed(true);
        authorizationCodeRepository.save(authCode);

        // 9. 토큰 생성 (JTI 포함)
        JwtUtil.TokenResult accessTokenResult = jwtUtil.generateAccessTokenWithJti(user);
        String accessToken = accessTokenResult.token();
        String jti = accessTokenResult.jti();
        String refreshToken = jwtUtil.generateRefreshToken(user);
        long expiresIn = 86400000;  // 24시간

        // 10. 세션 생성 (PKCE 여부 저장)
        sessionService.createSession(user, refreshToken, jti, request, isPkceFlow);

        if (isPkceFlow) {
            log.info("Authorization code exchanged for tokens with PKCE (Public Client): clientId={}, email={}", clientId, authCode.getEmail());
        } else {
            log.info("Authorization code exchanged for tokens with client_secret (Confidential Client): clientId={}, email={}", clientId, authCode.getEmail());
        }

        return OAuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .scope("user:email user:profile")
                .build();
    }

    @Override
    public OAuthTokenResponse refreshAccessToken(String refreshToken, String clientId, String clientSecret,
                                                 HttpServletRequest request) {
        // 1. Refresh Token 유효성 검증 (JWT 서명)
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        // 2. 세션 유효성 검증 (DB)
        if (!sessionService.validateSession(refreshToken)) {
            throw new BadCredentialsException("Session has been revoked");
        }

        // 3. 세션에서 PKCE 여부 확인
        String oldSessionHash = sessionService.hashToken(refreshToken);
        UserSession oldSession = userSessionRepository.findById(oldSessionHash)
                .orElseThrow(() -> new BadCredentialsException("Session not found"));

        boolean isPkceFlow = oldSession.getPkceFlow() != null && oldSession.getPkceFlow();

        // 4. Client 검증
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new BadCredentialsException("Invalid client"));

        // Confidential Client (PKCE 아님)인 경우 client_secret 검증
        if (!isPkceFlow) {
            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new BadCredentialsException("client_secret is required for non-PKCE session");
            }
            if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
                throw new BadCredentialsException("Invalid client credentials");
            }
        }

        if (!client.getEnabled()) {
            throw new BadCredentialsException("Client is disabled");
        }

        // 5. 사용자 조회
        String email = jwtUtil.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // 6. 기존 세션에서 이전 Access Token JTI 가져와서 블랙리스트 등록
        if (oldSession.getAccessTokenJti() != null) {
            // Access Token 남은 만료 시간 계산 (대략 15분으로 설정)
            tokenBlacklistService.blacklistJti(oldSession.getAccessTokenJti(), 900);
        }

        // 7. 새 토큰 생성 (토큰 로테이션)
        JwtUtil.TokenResult newAccessTokenResult = jwtUtil.generateAccessTokenWithJti(user);
        String newAccessToken = newAccessTokenResult.token();
        String newJti = newAccessTokenResult.jti();
        String newRefreshToken = jwtUtil.generateRefreshToken(user);
        long expiresIn = 86400000;  // 24시간

        // 8. 기존 세션 무효화 (refresh token 해시로 찾아서)
        sessionService.revokeSession(email, oldSessionHash, null);

        // 9. 새 세션 생성 (PKCE 여부 유지)
        sessionService.createSession(user, newRefreshToken, newJti, request, isPkceFlow);

        if (isPkceFlow) {
            log.info("OAuth token refreshed (Public Client/PKCE): email={}, clientId={}", email, clientId);
        } else {
            log.info("OAuth token refreshed (Confidential Client): email={}, clientId={}", email, clientId);
        }

        return OAuthTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .scope("user:email user:profile")
                .build();
    }

    @Override
    public void logout(String email, String refreshToken) {
        // 1. 세션 찾기
        String sessionHash = sessionService.hashToken(refreshToken);

        // 2. 해당 세션의 Access Token JTI를 블랙리스트에 추가
        userSessionRepository.findById(sessionHash).ifPresent(session -> {
            if (session.getAccessTokenJti() != null) {
                // Access Token 남은 만료 시간 계산 (최대 24시간)
                tokenBlacklistService.blacklistJti(session.getAccessTokenJti(), 86400);
            }
        });

        // 3. 세션 무효화
        sessionService.revokeSession(email, sessionHash, null);

        log.info("OAuth logout successful: email={}", email);
    }
}
