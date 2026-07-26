package kr.hyfata.rest.api.common.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Spring Authorization Server(SAS) 코어 설정
 * <p>
 * 기존 수동 OAuth 구현(/oauth/**)과 공존하도록 SAS 전용 필터 체인만 추가한다.
 * SAS 체인은 {@code @Order(1)} + SAS 엔드포인트 securityMatcher로 제한되고,
 * 나머지 요청은 기존 SecurityConfig의 필터 체인(기본 순서)이 처리한다.
 */
@Configuration
@Slf4j
public class AuthorizationServerConfig {

    /**
     * SAS 전용 SecurityFilterChain.
     * applyDefaultSecurity()가 SAS 프로토콜 엔드포인트만 매칭하는 securityMatcher를 설정한다.
     * 인증이 필요한 authorize 요청(HTML)은 커스텀 로그인 페이지(/oauth/login)로 리다이렉트한다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, configurer -> {
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/oauth/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    /**
     * SAS 엔드포인트 경로를 기존 수동 구현과 동일한 경로로 오버라이드 (클리이언트 파괴 최소화)
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .authorizationEndpoint("/oauth/authorize")
                .tokenEndpoint("/oauth/token")
                .tokenRevocationEndpoint("/oauth/revoke")
                .tokenIntrospectionEndpoint("/oauth/introspect")
                .jwkSetEndpoint("/oauth/jwks")
                .build();
    }

    /**
     * RegisteredClient 저장소 (oauth2_registered_client 테이블).
     * SAS 표준 JdbcRegisteredClientRepository 사용. 클라이언트 등록/갱신은
     * ClientServiceImpl(third-party)과 FirstPartyClientInitializer(first-party)가 수행한다.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * OAuth2Authorization 저장소 (oauth2_authorization 테이블).
     * SessionBridgingAuthorizationService(@Component)가 JdbcOAuth2AuthorizationService를 감싼
     * 데코레이터로 등록되므로 별도 @Bean 정의는 두지 않는다.
     */

    /**
     * OAuth2AuthorizationConsent 저장소 (oauth2_authorization_consent 테이블)
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
                                                                         RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * Access Token 클레임 커스터마이저.
     * 기존 JwtUtil 토큰과 호환되도록 email(주체), client_id, 공백 구분 scope 클레임을 추가한다.
     * (iss, sub, jti, exp 등은 SAS가 기본 제공)
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().claim("email", context.getPrincipal().getName());
                context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
                context.getClaims().claim("scope", String.join(" ", context.getAuthorizedScopes()));
            }
        };
    }

    /**
     * RSA 키페어 로딩.
     * auth.jwt.private-key / auth.jwt.public-key (classpath: 또는 file: 경로, PEM 형식)에서 로드하고,
     * 미설정 시 개발 편의를 위해 시작 시 RSA 2048 키페어를 생성한다 (재시작 시 기존 토큰 무효화됨).
     * <p>
     * PEM 형식: 개인키는 PKCS#8("BEGIN PRIVATE KEY"), 공개키는 X.509("BEGIN PUBLIC KEY").
     */
    @Bean
    public KeyPair rsaKeyPair(@Value("${auth.jwt.private-key:}") String privateKeyPath,
                              @Value("${auth.jwt.public-key:}") String publicKeyPath,
                              ResourceLoader resourceLoader) throws Exception {
        if (StringUtils.hasText(privateKeyPath) && StringUtils.hasText(publicKeyPath)) {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(readPem(resourceLoader.getResource(publicKeyPath), "PUBLIC KEY")));
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(readPem(resourceLoader.getResource(privateKeyPath), "PRIVATE KEY")));
            log.info("RSA 키페어를 파일에서 로드했습니다. (private: {}, public: {})", privateKeyPath, publicKeyPath);
            return new KeyPair(publicKey, privateKey);
        }

        log.warn("auth.jwt.private-key / auth.jwt.public-key가 설정되지 않아 임시 RSA 2048 키페어를 생성합니다. "
                + "개발 환경 전용 — 서버 재시작 시 기존 토큰이 모두 무효화됩니다.");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Resource Server 디코더 등에서 재사용할 수 있도록 공개키를 별도 빈으로 노출
     */
    @Bean
    public RSAPublicKey rsaPublicKey(KeyPair rsaKeyPair) {
        return (RSAPublicKey) rsaKeyPair.getPublic();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(KeyPair rsaKeyPair) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) rsaKeyPair.getPublic())
                .privateKey((RSAPrivateKey) rsaKeyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Resource Server용 JWT 디코더.
     * SAS와 동일한 RSA 키페어의 공개키로 RS256 서명을 검증한다 (로컬 검증, 네트워크 호출 없음).
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey) {
        return NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
    }

    /**
     * PEM 파일에서 Base64 본문만 추출해 디코딩
     */
    private byte[] readPem(Resource resource, String pemType) throws Exception {
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String base64 = pem
                .replace("-----BEGIN " + pemType + "-----", "")
                .replace("-----END " + pemType + "-----", "")
                .replaceAll("\\s", "");
        if (!StringUtils.hasText(base64)) {
            throw new IllegalArgumentException("PEM 파싱 실패 (" + pemType + "): " + resource.getDescription());
        }
        return Base64.getDecoder().decode(base64);
    }
}
