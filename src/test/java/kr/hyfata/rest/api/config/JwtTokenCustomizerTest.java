package kr.hyfata.rest.api.config;

import kr.hyfata.rest.api.common.config.AuthorizationServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * SAS JWT 커스터마이저 테스트 — 기존 JwtUtil 토큰과의 클레임 호환(email, client_id, scope)
 */
class JwtTokenCustomizerTest {

    private final OAuth2TokenCustomizer<JwtEncodingContext> customizer =
            new AuthorizationServerConfig().jwtTokenCustomizer();

    private final RegisteredClient registeredClient = RegisteredClient.withId("test-client")
            .clientId("test-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://app.example.com/callback")
            .build();

    @Test
    @DisplayName("access token - email, client_id, 공백 구분 scope 클레임 추가")
    void customize_accessToken_addsCompatibleClaims() {
        // given
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getPrincipal()).thenReturn(
                new UsernamePasswordAuthenticationToken("user@example.com", null));
        when(context.getRegisteredClient()).thenReturn(registeredClient);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("profile", "email"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        // when
        customizer.customize(context);

        // then
        JwtClaimsSet claims = claimsBuilder.build();
        assertThat(claims.getClaimAsString("email")).isEqualTo("user@example.com");
        assertThat(claims.getClaimAsString("client_id")).isEqualTo("test-client");
        assertThat(claims.getClaimAsString("scope")).contains("profile", "email");
        assertThat((claims.getClaimAsString("scope")).split(" ")).hasSize(2);
    }

    @Test
    @DisplayName("access token이 아니면 클레임을 추가하지 않음")
    void customize_nonAccessToken_addsNothing() {
        // given
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder();

        when(context.getTokenType()).thenReturn(new OAuth2TokenType("refresh_token"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        // when
        customizer.customize(context);

        // then (클레임 소스 조회가 호출되지 않아야 함)
        verify(context, never()).getPrincipal();
        verify(context, never()).getRegisteredClient();
        verify(context, never()).getAuthorizedScopes();
    }
}
