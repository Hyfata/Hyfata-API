package kr.hyfata.rest.api.service.impl;

import kr.hyfata.rest.api.dto.OAuthTokenResponse;
import kr.hyfata.rest.api.entity.AuthorizationCode;
import kr.hyfata.rest.api.entity.Client;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.repository.AuthorizationCodeRepository;
import kr.hyfata.rest.api.repository.ClientRepository;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.service.OAuthService;
import kr.hyfata.rest.api.util.JwtUtil;
import kr.hyfata.rest.api.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthServiceImpl implements OAuthService {

    private final AuthorizationCodeRepository authorizationCodeRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final TokenGenerator tokenGenerator;

    @Override
    public String generateAuthorizationCode(String clientId, String email, String redirectUri, String state) {
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

        AuthorizationCode authCode = AuthorizationCode.builder()
                .code(code)
                .clientId(clientId)
                .email(email)
                .redirectUri(redirectUri)
                .state(state)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(10))  // 10분 유효
                .build();

        authorizationCodeRepository.save(authCode);
        log.info("Authorization code generated for client: {}, email: {}", clientId, email);

        return code;
    }

    @Override
    public OAuthTokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri) {
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

        // 5. Client Secret 검증
        Client client = clientRepository.findByClientIdAndClientSecret(clientId, clientSecret)
                .orElseThrow(() -> new BadCredentialsException("Invalid client credentials"));

        if (!client.getEnabled()) {
            throw new BadCredentialsException("Client is disabled");
        }

        // 6. 사용자 조회
        User user = userRepository.findByEmail(authCode.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // 7. 코드 사용 표시
        authCode.setUsed(true);
        authorizationCodeRepository.save(authCode);

        // 8. 토큰 생성
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        long expiresIn = 86400000;  // 24시간

        log.info("Authorization code exchanged for tokens: clientId={}, email={}", clientId, authCode.getEmail());

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
}
