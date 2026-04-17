package kr.hyfata.rest.api.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.auth.dto.OAuthTokenResponse;

public interface OAuthService {
    /**
     * Authorization Code 생성 (PKCE 없음)
     */
    String generateAuthorizationCode(String clientId, String email, String redirectUri, String state);

    /**
     * Authorization Code 생성 (PKCE 포함)
     */
    String generateAuthorizationCode(String clientId, String email, String redirectUri, String state,
                                     String codeChallenge, String codeChallengeMethod);

    /**
     * Authorization Code 검증 및 토큰 발급 (PKCE 없음)
     */
    OAuthTokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri);

    /**
     * Authorization Code 검증 및 토큰 발급 (PKCE 포함)
     */
    OAuthTokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri, String codeVerifier);

    /**
     * Authorization Code 유효성 검증
     */
    boolean validateAuthorizationCode(String code, String clientId);

    /**
     * Redirect URI 유효성 검증
     */
    boolean validateRedirectUri(String clientId, String redirectUri);

    /**
     * State 파라미터 유효성 검증 (CSRF 방지)
     */
    boolean validateState(String code, String state);

    /**
     * Authorization Code 검증 및 토큰 발급 (PKCE 포함, 세션 관리)
     */
    OAuthTokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret,
                                            String redirectUri, String codeVerifier, HttpServletRequest request);

    /**
     * Refresh Token으로 새 Access Token 발급
     */
    OAuthTokenResponse refreshAccessToken(String refreshToken, String clientId, String clientSecret,
                                          HttpServletRequest request);

    /**
     * OAuth 로그아웃 (세션 무효화 및 토큰 블랙리스트)
     */
    void logout(String email, String refreshToken);
}
