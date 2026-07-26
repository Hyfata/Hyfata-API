package kr.hyfata.rest.api.security;

import kr.hyfata.rest.api.infrastructure.security.scope.RequireScope;
import kr.hyfata.rest.api.infrastructure.security.scope.ScopeAuthorizationAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * ScopeAuthorizationAspect 테스트 (Resource Server authority 모델 기준)
 * SCOPE_ prefix authority를 SecurityContext에 설정해 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScopeAuthorizationAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    private ScopeAuthorizationAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new ScopeAuthorizationAspect();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("필요한 scope를 가진 토큰 - 접근 허용")
    void checkScope_withValidScope_allowsAccess() throws Throwable {
        // given
        setAuthenticationScopes("profile", "email", "account:password");

        RequireScope requireScope = createRequireScope(new String[]{"account:password"}, new String[]{});
        when(joinPoint.proceed()).thenReturn("success");

        // when
        Object result = aspect.checkScope(joinPoint, requireScope);

        // then
        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("필요한 scope가 없는 토큰 - 접근 거부")
    void checkScope_withoutRequiredScope_deniesAccess() {
        // given
        setAuthenticationScopes("profile", "email");

        RequireScope requireScope = createRequireScope(new String[]{"account:password"}, new String[]{});

        // when & then
        assertThatThrownBy(() -> aspect.checkScope(joinPoint, requireScope))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient scope");
    }

    @Test
    @DisplayName("암시적 scope 포함 - profile:write가 profile을 커버")
    void checkScope_implicitScope_profileWriteCoversProfile() throws Throwable {
        // given
        setAuthenticationScopes("profile:write", "email");

        RequireScope requireScope = createRequireScope(new String[]{"profile"}, new String[]{});
        when(joinPoint.proceed()).thenReturn("success");

        // when
        Object result = aspect.checkScope(joinPoint, requireScope);

        // then
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("암시적 scope 포함 - account:manage가 account:password를 커버")
    void checkScope_implicitScope_accountManageCoversPassword() throws Throwable {
        // given
        setAuthenticationScopes("profile", "email", "account:manage");

        RequireScope requireScope = createRequireScope(new String[]{"account:password"}, new String[]{});
        when(joinPoint.proceed()).thenReturn("success");

        // when
        Object result = aspect.checkScope(joinPoint, requireScope);

        // then
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("AND 조건 - 모든 scope를 만족해야 접근 허용")
    void checkScope_allCondition_success() throws Throwable {
        // given
        setAuthenticationScopes("profile", "email", "account:manage", "2fa:manage");

        RequireScope requireScope = createRequireScope(new String[]{}, new String[]{"account:manage", "2fa:manage"});
        when(joinPoint.proceed()).thenReturn("success");

        // when
        Object result = aspect.checkScope(joinPoint, requireScope);

        // then
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("AND 조건 - 하나라도 부족하면 접근 거부")
    void checkScope_allCondition_fails() {
        // given
        setAuthenticationScopes("profile", "email", "account:manage");

        RequireScope requireScope = createRequireScope(new String[]{}, new String[]{"account:manage", "2fa:manage"});

        // when & then
        assertThatThrownBy(() -> aspect.checkScope(joinPoint, requireScope))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient scope. Required all");
    }

    @Test
    @DisplayName("OR 조건 - 여러 scope 중 하나라도 만족하면 접근 허용")
    void checkScope_orCondition_success() throws Throwable {
        // given
        setAuthenticationScopes("profile", "email", "sessions:manage");

        RequireScope requireScope = createRequireScope(new String[]{"account:manage", "sessions:manage"}, new String[]{});
        when(joinPoint.proceed()).thenReturn("success");

        // when
        Object result = aspect.checkScope(joinPoint, requireScope);

        // then
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("JWT 인증 없음 - 접근 거부")
    void checkScope_noJwtAuthentication_deniesAccess() {
        // given (SecurityContext에 인증 정보 없음)
        RequireScope requireScope = createRequireScope(new String[]{"profile"}, new String[]{});

        // when & then
        assertThatThrownBy(() -> aspect.checkScope(joinPoint, requireScope))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("JWT token is required");
    }

    /**
     * SecurityContext에 SCOPE_ prefix authority를 가진 JWT 인증 설정
     */
    private void setAuthenticationScopes(String... scopes) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(scopes)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();

        Jwt jwt = mock(Jwt.class);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private RequireScope createRequireScope(String[] value, String[] all) {
        RequireScope annotation = mock(RequireScope.class);
        when(annotation.value()).thenReturn(value);
        when(annotation.all()).thenReturn(all);
        return annotation;
    }
}
