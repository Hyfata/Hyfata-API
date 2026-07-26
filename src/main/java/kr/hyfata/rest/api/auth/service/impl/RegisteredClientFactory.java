package kr.hyfata.rest.api.auth.service.impl;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * RegisteredClient 생성 팩토리
 * <p>
 * 클라이언트 등록 경로(ClientServiceImpl: third-party, FirstPartyClientInitializer: first-party)가
 * 동일한 프로토콜 규칙으로 RegisteredClient를 생성하도록 한다:
 * - confidential(시크릿 존재): CLIENT_SECRET_BASIC + BCrypt 해시 그대로
 * - public(시크릿 없음): NONE
 * - 모든 클라이언트 PKCE 필수 (OAuth 2.1)
 * - consent 필요 여부는 클라이언트 유형에 따라 설정 (third-party: true, first-party: false)
 * - TokenSettings: access 15분, refresh 14일, refresh 로테이션(reuseRefreshTokens=false)
 */
public final class RegisteredClientFactory {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

    private RegisteredClientFactory() {
    }

    /**
     * @param clientId 클라이언트 ID (RegisteredClient.id도 동일 값 사용)
     * @param clientSecretHash BCrypt 해시된 시크릿. null이면 public(NONE) 클라이언트
     * @param clientName 클라이언트 이름
     * @param redirectUris 등록된 redirect URI 집합
     * @param scopes 허용 scope 집합
     * @param requireConsent consent 화면 필요 여부 (third-party: true, first-party: false)
     */
    public static RegisteredClient build(String clientId, String clientSecretHash, String clientName,
                                         Set<String> redirectUris, Set<String> scopes, boolean requireConsent) {
        boolean confidential = clientSecretHash != null && !clientSecretHash.isBlank();

        RegisteredClient.Builder builder = RegisteredClient.withId(clientId)
                .clientId(clientId)
                .clientIdIssuedAt(Instant.now())
                .clientName(clientName)
                .clientAuthenticationMethod(confidential
                        ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                        : ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(redirectUris))
                .scopes(s -> s.addAll(scopes))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(requireConsent)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                        .reuseRefreshTokens(false)
                        .build());

        if (confidential) {
            builder.clientSecret(clientSecretHash);
        }

        return builder.build();
    }
}
