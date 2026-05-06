package kr.hyfata.rest.api.security;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.common.security.scope.RequireScope;
import kr.hyfata.rest.api.common.security.scope.ScopeAuthorizationAspect;
import kr.hyfata.rest.api.common.util.JwtUtil;
import org.aspectj.lang.ProceedingJoinPoint;
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
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScopeAuthorizationAspectTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private HttpServletRequest request;

    private ScopeAuthorizationAspect aspect;
    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        aspect = new ScopeAuthorizationAspect(jwtUtil);
        testUser = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("필요한 scope를 가진 토큰 - 접근 허용")
    void checkScope_withValidScope_allowsAccess() throws Throwable {
        // given
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractScopes(token)).thenReturn(Set.of("profile", "email", "account:password"));

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
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractScopes(token)).thenReturn(Set.of("profile", "email"));

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
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractScopes(token)).thenReturn(Set.of("profile:write", "email"));

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
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractScopes(token)).thenReturn(Set.of("profile", "email", "account:manage"));

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
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractScopes(token)).thenReturn(Set.of("profile", "email", "account:manage", "2fa:manage"));

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
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractScopes(token)).thenReturn(Set.of("profile", "email", "account:manage"));

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
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractScopes(token)).thenReturn(Set.of("profile", "email", "sessions:manage"));

        RequireScope requireScope = createRequireScope(new String[]{"account:manage", "sessions:manage"}, new String[]{});
        when(joinPoint.proceed()).thenReturn("success");

        // when
        Object result = aspect.checkScope(joinPoint, requireScope);

        // then
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("Authorization 헤더 없음 - 접근 거부")
    void checkScope_noAuthHeader_deniesAccess() {
        // given
        when(request.getHeader("Authorization")).thenReturn(null);

        RequireScope requireScope = createRequireScope(new String[]{"profile"}, new String[]{});

        // when & then
        assertThatThrownBy(() -> aspect.checkScope(joinPoint, requireScope))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("JWT token is required");
    }

    private RequireScope createRequireScope(String[] value, String[] all) {
        RequireScope annotation = mock(RequireScope.class);
        when(annotation.value()).thenReturn(value);
        when(annotation.all()).thenReturn(all);
        return annotation;
    }
}
