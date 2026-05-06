package kr.hyfata.rest.api.common.security.scope;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link RequireScope} 어노테이션을 처리하는 AOP Aspect
 */
@Component
@Aspect
@RequiredArgsConstructor
@Slf4j
public class ScopeAuthorizationAspect {

    private final JwtUtil jwtUtil;

    @Around("@annotation(requireScope)")
    public Object checkScope(ProceedingJoinPoint joinPoint, RequireScope requireScope) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String jwt = extractJwtFromRequest(request);

        if (jwt == null) {
            throw new AccessDeniedException("JWT token is required");
        }

        Set<String> tokenScopes = jwtUtil.extractScopes(jwt);

        // AND 조건 검증
        if (requireScope.all().length > 0) {
            for (String required : requireScope.all()) {
                if (!hasScope(tokenScopes, required)) {
                    throw new AccessDeniedException(
                            "Insufficient scope. Required all: " + Arrays.toString(requireScope.all()));
                }
            }
        }

        // OR 조건 검증 (value)
        if (requireScope.value().length > 0) {
            boolean hasAny = Arrays.stream(requireScope.value())
                    .anyMatch(req -> hasScope(tokenScopes, req));

            if (!hasAny) {
                throw new AccessDeniedException(
                        "Insufficient scope. Required one of: " + Arrays.toString(requireScope.value()));
            }
        }

        return joinPoint.proceed();
    }

    /**
     * 토큰의 scope 목록에서 필요한 scope를 가지고 있는지 확인
     * 암시적 포함 관계도 검증 (profile:write → profile, account:manage → account:password)
     */
    private boolean hasScope(Set<String> tokenScopes, String required) {
        if (tokenScopes.contains(required)) {
            return true;
        }
        return hasImplicitScope(tokenScopes, required);
    }

    /**
     * 암시적 scope 포함 관계 체크
     */
    private boolean hasImplicitScope(Set<String> tokenScopes, String required) {
        // profile:write → profile 암시적 포함
        if ("profile".equals(required) && tokenScopes.contains("profile:write")) {
            return true;
        }
        // account:manage → account:password 암시적 포함
        if ("account:password".equals(required) && tokenScopes.contains("account:manage")) {
            return true;
        }
        // account:manage → account:manage (이미 위에서 체크됨)
        return false;
    }

    /**
     * Authorization 헤더에서 JWT 토큰 추출
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        final String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
