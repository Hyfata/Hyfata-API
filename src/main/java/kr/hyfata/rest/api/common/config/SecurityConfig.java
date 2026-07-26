package kr.hyfata.rest.api.common.config;

import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.service.CustomUserDetailsService;
import kr.hyfata.rest.api.common.security.EmailNotVerifiedException;
import kr.hyfata.rest.api.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * 애플리케이션 SecurityFilterChain (@Order(2))
 * <p>
 * SAS 프로토콜 엔드포인트는 @Order(1)의 AuthorizationServerConfig 체인이 먼저 처리하고,
 * 나머지 요청(API, 로그인 페이지, 정적 리소스 등)은 이 체인이 처리한다.
 * JWT 인증은 Resource Server(oauth2ResourceServer().jwt(), RS256)가 담당하고,
 * JwtAuthenticationFilter는 민감 엔드포인트의 JTI 블랙리스트 검사만 수행한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * OAuth 로그인(formLogin)용 인증 프로바이더.
     * 계정 상태(enabled/locked 등)는 기본 pre-checks가 처리하고,
     * 이메일 인증 여부는 post-check로 확인한다 (기존 OAuthController의 수동 체크 이전).
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(CustomUserDetailsService userDetailsService,
                                                               PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPostAuthenticationChecks(userDetails -> {
            if (userDetails instanceof User user && !Boolean.TRUE.equals(user.getEmailVerified())) {
                throw new EmailNotVerifiedException("이메일 인증이 필요합니다.");
            }
        });
        return provider;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authz -> authz
                        // 인증 필요 엔드포인트 (명시적)
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/enable-2fa").authenticated()
                        .requestMatchers("/api/auth/disable-2fa").authenticated()
                        .requestMatchers("/api/sessions/**").authenticated()
                        .requestMatchers("/oauth/logout").authenticated()
                        // 공개 엔드포인트
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/account/restore/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/oauth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/reset-password").permitAll()
                        .requestMatchers("/verify-email").permitAll()
                        // Swagger/OpenAPI 관련 엔드포인트 (필요시 추가)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // OAuth 로그인 폼 (SAS authorize 요청은 request cache에 저장되어 로그인 성공 후 자동 재개)
                .formLogin(form -> form
                        .loginPage("/oauth/login")
                        .loginProcessingUrl("/oauth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .failureHandler(oauthLoginFailureHandler())
                        .permitAll())
                // Resource Server: RS256 JWT 검증 (SAS와 동일한 RSA 공개키)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                // Resource Server 인증 이후 민감 엔드포인트의 JTI 블랙리스트 검사
                .addFilterAfter(jwtAuthenticationFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 로그인 실패 시 에러 유형별 파라미터로 로그인 페이지에 리다이렉트.
     * credentials: 이메일/비밀번호 오류, disabled: 비활성/잠긴 계정, unverified: 이메일 미인증
     */
    private AuthenticationFailureHandler oauthLoginFailureHandler() {
        return (request, response, exception) -> {
            String error = "credentials";
            if (exception instanceof EmailNotVerifiedException) {
                error = "unverified";
            } else if (exception instanceof AccountStatusException) {
                error = "disabled";
            }
            response.sendRedirect("/oauth/login?error=" + error);
        };
    }

    /**
     * scope 클레임(공백 구분 문자열)을 SCOPE_ prefix authority로 매핑하는 컨버터.
     * 예: scope="profile email" → SCOPE_profile, SCOPE_email
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
