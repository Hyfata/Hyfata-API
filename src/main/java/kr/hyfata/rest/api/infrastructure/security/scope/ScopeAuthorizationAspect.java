package kr.hyfata.rest.api.infrastructure.security.scope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RequireScope} 어노테이션을 처리하는 AOP Aspect
 * <p>
 * Resource Server가 SecurityContext에 설정한 SCOPE_ prefix authority를 기준으로 검증한다.
 * 세션 기반 인증 등 JWT 인증이 아닌 요청은 scope가 없으므로 거부된다.
 */
@Component
@Aspect
@RequiredArgsConstructor
@Slf4j
public class ScopeAuthorizationAspect {

    private static final String SCOPE_PREFIX = "SCOPE_";

    @Around("@annotation(requireScope)")
    public Object checkScope(ProceedingJoinPoint joinPoint, RequireScope requireScope) throws Throwable {
        Set<String> tokenScopes = extractTokenScopes();

        if (tokenScopes == null) {
            throw new AccessDeniedException("JWT token is required");
        }

        // AND 조건 검증
        for (String required : requireScope.all()) {
            if (!hasScope(tokenScopes, required)) {
                throw new AccessDeniedException(
                        "Insufficient scope. Required all: " + Arrays.toString(requireScope.all()));
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
     * SecurityContext의 SCOPE_ prefix authority에서 scope 목록 추출.
     * JWT 인증이 아니면 null 반환.
     */
    private Set<String> extractTokenScopes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            return null;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(SCOPE_PREFIX))
                .map(authority -> authority.substring(SCOPE_PREFIX.length()))
                .collect(Collectors.toSet());
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
}
