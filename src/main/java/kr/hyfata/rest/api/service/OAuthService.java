package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.dto.OAuthTokenResponse;

public interface OAuthService {
    /**
     * Authorization Code 생성
     */
    String generateAuthorizationCode(String clientId, String email, String redirectUri, String state);

    /**
     * Authorization Code 검증 및 토큰 발급
     */
    OAuthTokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri);

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
}
