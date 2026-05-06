package kr.hyfata.rest.api.auth.service.impl;

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
import kr.hyfata.rest.api.auth.service.OAuthService;
import kr.hyfata.rest.api.auth.service.SessionService;
import kr.hyfata.rest.api.auth.service.TokenBlacklistService;
import kr.hyfata.rest.api.common.util.JwtUtil;
import kr.hyfata.rest.api.common.util.PkceUtil;
import kr.hyfata.rest.api.common.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
        return generateAuthorizationCode(clientId, email, redirectUri, state, null, null, null);
    }

    @Override
    public String generateAuthorizationCode(String clientId, String email, String redirectUri, String state,
                                           String scopes) {
        return generateAuthorizationCode(clientId, email, redirectUri, state, null, null, scopes);
    }

    @Override
    public String generateAuthorizationCode(String clientId, String email, String redirectUri, String state,
                                           String codeChallenge, String codeChallengeMethod) {
        return generateAuthorizationCode(clientId, email, redirectUri, state, codeChallenge, codeChallengeMethod, null);
    }

    @Override
    public String generateAuthorizationCode(String clientId, String email, String redirectUri, String state,
                                           String codeChallenge, String codeChallengeMethod, String scopes) {
        // 클라이언트 검증
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new BadCredentialsException("유효하지 않은 클라이언트입니다."));

        if (!client.getEnabled()) {
            throw new BadCredentialsException("비활성화된 클라이언트입니다.");
        }

        // Redirect URI 검증
        if (!validateRedirectUri(clientId, redirectUri)) {
            throw new BadCredentialsException("유효하지 않은 redirect URI입니다.");
        }

        // 사용자 검증
        userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        // Authorization Code 생성
        String code = tokenGenerator.generatePasswordResetToken();  // 긴 난수 토큰

        AuthorizationCode.AuthorizationCodeBuilder builder = AuthorizationCode.builder()
                .code(code)
                .clientId(clientId)
                .email(email)
                .redirectUri(redirectUri)
                .state(state)
                .scopes(scopes)
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
                .orElseThrow(() -> new BadCredentialsException("유효하지 않은 인증 코드입니다."));

        // 2. 코드 만료 여부 확인
        if (LocalDateTime.now().isAfter(authCode.getExpiresAt())) {
            authorizationCodeRepository.delete(authCode);
            throw new BadCredentialsException("인증 코드가 만료되었습니다.");
        }

        // 3. 코드 사용 여부 확인 (한 번만 사용 가능)
        if (authCode.getUsed()) {
            throw new BadCredentialsException("이미 사용된 인증 코드입니다.");
        }

        // 4. Redirect URI 검증
        if (!authCode.getRedirectUri().equals(redirectUri)) {
            throw new BadCredentialsException("redirect URI가 일치하지 않습니다.");
        }

        // 5. PKCE 검증 (code_challenge가 저장되어 있으면 code_verifier 필수)
        if (authCode.getCodeChallenge() != null && !authCode.getCodeChallenge().isEmpty()) {
            if (codeVerifier == null || codeVerifier.isEmpty()) {
                throw new BadCredentialsException("PKCE 흐름에서는 code_verifier가 필요합니다.");
            }

            // code_verifier 유효성 검증
            if (!pkceUtil.isValidCodeVerifier(codeVerifier)) {
                throw new BadCredentialsException("code_verifier 형식이 올바르지 않습니다.");
            }

            // code_verifier와 code_challenge 검증
            if (!pkceUtil.verifyCodeChallenge(codeVerifier, authCode.getCodeChallenge())) {
                log.warn("PKCE verification failed: clientId={}, email={}", clientId, authCode.getEmail());
                throw new BadCredentialsException("code_verifier 검증에 실패했습니다.");
            }

            log.debug("PKCE verification successful: clientId={}, email={}", clientId, authCode.getEmail());
        }

        // 6. Client Secret 검증
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new BadCredentialsException("유효하지 않은 클라이언트 인증 정보입니다."));

        // BCrypt로 저장된 clientSecret과 비교
        if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
            throw new BadCredentialsException("Invalid client credentials");
        }

        if (!client.getEnabled()) {
            throw new BadCredentialsException("비활성화된 클라이언트입니다.");
        }

        // 7. 사용자 조회
        User user = userRepository.findByEmail(authCode.getEmail())
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        // 8. 코드 사용 표시
        authCode.setUsed(true);
        authorizationCodeRepository.save(authCode);

        // 9. 토큰 생성 (scope 포함)
        Set<String> scopes = parseScopes(authCode.getScopes());
        JwtUtil.TokenResult accessTokenResult = jwtUtil.generateAccessTokenWithJti(user, clientId, scopes);
        String accessToken = accessTokenResult.token();
        String jti = accessTokenResult.jti();
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
                .scope(String.join(" ", scopes))
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
        if (redirectUri == null || redirectUri.isBlank()) {
            return false;
        }
        String trimmedRedirectUri = redirectUri.trim();
        return clientRepository.findByClientId(clientId)
                .map(client -> {
                    String[] redirectUris = client.getRedirectUris().split(",");
                    return Arrays.stream(redirectUris)
                            .map(String::trim)
                            .anyMatch(uri -> uri.equals(trimmedRedirectUri));
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
                .orElseThrow(() -> new BadCredentialsException("유효하지 않은 인증 코드입니다."));

        // 2. 코드 만료 여부 확인
        if (LocalDateTime.now().isAfter(authCode.getExpiresAt())) {
            authorizationCodeRepository.delete(authCode);
            throw new BadCredentialsException("인증 코드가 만료되었습니다.");
        }

        // 3. 코드 사용 여부 확인 (한 번만 사용 가능)
        if (authCode.getUsed()) {
            throw new BadCredentialsException("이미 사용된 인증 코드입니다.");
        }

        // 4. Redirect URI 검증
        if (!authCode.getRedirectUri().equals(redirectUri)) {
            throw new BadCredentialsException("redirect URI가 일치하지 않습니다.");
        }

        // 5. PKCE 또는 Client Secret 검증
        boolean isPkceFlow = authCode.getCodeChallenge() != null && !authCode.getCodeChallenge().isEmpty();

        if (isPkceFlow) {
            // Public Client (PKCE Flow) - code_verifier로 검증, client_secret 불필요
            if (codeVerifier == null || codeVerifier.isEmpty()) {
                throw new BadCredentialsException("PKCE 흐름에서는 code_verifier가 필요합니다.");
            }

            // code_verifier 유효성 검증
            if (!pkceUtil.isValidCodeVerifier(codeVerifier)) {
                throw new BadCredentialsException("code_verifier 형식이 올바르지 않습니다.");
            }

            // code_verifier와 code_challenge 검증
            if (!pkceUtil.verifyCodeChallenge(codeVerifier, authCode.getCodeChallenge())) {
                log.warn("PKCE verification failed: clientId={}, email={}", clientId, authCode.getEmail());
                throw new BadCredentialsException("code_verifier 검증에 실패했습니다.");
            }

            log.debug("PKCE verification successful: clientId={}, email={}", clientId, authCode.getEmail());
        } else {
            // Confidential Client - client_secret으로 검증
            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new BadCredentialsException("PKCE가 아닌 흐름에서는 client_secret이 필요합니다.");
            }
        }

        // 6. Client 검증
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new BadCredentialsException("유효하지 않은 클라이언트입니다."));

        // Confidential Client인 경우 client_secret 검증
        if (!isPkceFlow) {
            if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
                throw new BadCredentialsException("Invalid client credentials");
            }
        }

        if (!client.getEnabled()) {
            throw new BadCredentialsException("비활성화된 클라이언트입니다.");
        }

        // 7. 사용자 조회
        User user = userRepository.findByEmail(authCode.getEmail())
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        // 8. 코드 사용 표시
        authCode.setUsed(true);
        authorizationCodeRepository.save(authCode);

        // 9. 토큰 생성 (JTI + client_id + scope 포함)
        Set<String> scopes = parseScopes(authCode.getScopes());
        JwtUtil.TokenResult accessTokenResult = jwtUtil.generateAccessTokenWithJti(user, clientId, scopes);
        String accessToken = accessTokenResult.token();
        String jti = accessTokenResult.jti();
        String refreshToken = jwtUtil.generateRefreshToken(user);
        long expiresIn = 86400000;  // 24시간

        // 10. 세션 생성 (PKCE 여부 및 scope 저장)
        sessionService.createSession(user, refreshToken, jti, request, isPkceFlow, scopes);

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
                .scope(String.join(" ", scopes))
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
            throw new BadCredentialsException("세션이 해지되었습니다.");
        }

        // 3. 세션에서 PKCE 여부 확인
        String oldSessionHash = sessionService.hashToken(refreshToken);
        UserSession oldSession = userSessionRepository.findById(oldSessionHash)
                .orElseThrow(() -> new BadCredentialsException("세션을 찾을 수 없습니다."));

        boolean isPkceFlow = oldSession.getPkceFlow() != null && oldSession.getPkceFlow();

        // 4. Client 검증
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new BadCredentialsException("유효하지 않은 클라이언트입니다."));

        // Confidential Client (PKCE 아님)인 경우 client_secret 검증
        if (!isPkceFlow) {
            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new BadCredentialsException("PKCE가 아닌 세션에서는 client_secret이 필요합니다.");
            }
            if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
                throw new BadCredentialsException("Invalid client credentials");
            }
        }

        if (!client.getEnabled()) {
            throw new BadCredentialsException("비활성화된 클라이언트입니다.");
        }

        // 5. 사용자 조회
        String email = jwtUtil.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        // 6. 기존 세션에서 이전 Access Token JTI 가져와서 블랙리스트 등록
        if (oldSession.getAccessTokenJti() != null) {
            // Access Token 남은 만료 시간 계산 (대략 15분으로 설정)
            tokenBlacklistService.blacklistJti(oldSession.getAccessTokenJti(), 900);
        }

        // 7. 기존 세션의 scope 유지
        Set<String> scopes = parseScopes(oldSession.getScopes());

        // 8. 새 토큰 생성 (토큰 로테이션, 기존 scope 유지)
        JwtUtil.TokenResult newAccessTokenResult = jwtUtil.generateAccessTokenWithJti(user, clientId, scopes);
        String newAccessToken = newAccessTokenResult.token();
        String newJti = newAccessTokenResult.jti();
        String newRefreshToken = jwtUtil.generateRefreshToken(user);
        long expiresIn = 86400000;  // 24시간

        // 9. 기존 세션 무효화 (refresh token 해시로 찾아서)
        sessionService.revokeSession(email, oldSessionHash, null);

        // 10. 새 세션 생성 (PKCE 여부 및 scope 유지)
        sessionService.createSession(user, newRefreshToken, newJti, request, isPkceFlow, scopes);

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
                .scope(String.join(" ", scopes))
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

    /**
     * scope 문자열을 Set으로 파싱 (null/empty 시 기본값)
     */
    private Set<String> parseScopes(String scopesStr) {
        if (scopesStr == null || scopesStr.isBlank()) {
            return new HashSet<>(Arrays.asList("profile", "email"));
        }
        return new HashSet<>(Arrays.asList(scopesStr.split(" ")));
    }
}
