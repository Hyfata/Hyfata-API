package kr.hyfata.rest.api.oauth;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * {@link org.springframework.security.oauth2.server.authorization.client.RegisteredClient} factory
 * for build consistently
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
