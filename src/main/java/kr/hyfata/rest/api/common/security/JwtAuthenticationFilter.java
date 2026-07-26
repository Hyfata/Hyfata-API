package kr.hyfata.rest.api.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hyfata.rest.api.auth.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JTI 블랙리스트 전용 필터
 * <p>
 * 토큰 파싱·서명 검증은 Resource Server(BearerTokenAuthenticationFilter)가 처리하므로,
 * 이 필터는 Resource Server 인증 이후 security.sensitive-endpoints에 매칭되는 경로에서만
 * SecurityContext의 Jwt에서 jti를 꺼내 블랙리스트 여부를 검사한다.
 * 세션 기반 인증(OAuth 로그인 폼 세션) 요청은 JWT가 없으므로 검사를 스킵한다.
 * Redis 장애 시 fail-open (TokenBlacklistService가 false 반환) — 현행 정책 유지.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenBlacklistService tokenBlacklistService;

    @Value("${security.sensitive-endpoints:/api/auth/change-password,/api/users/me,/api/payments,/api/sessions}")
    private String sensitiveEndpointsConfig;

    private List<String> sensitiveEndpoints;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isSensitiveEndpoint(request.getRequestURI())) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // JWT 기반 인증인 경우에만 블랙리스트 검사 (세션 기반 인증은 스킵)
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                String jti = jwtAuthentication.getToken().getId();
                if (jti != null && tokenBlacklistService.isJtiBlacklisted(jti)) {
                    log.warn("Blocked request with revoked token to sensitive endpoint: {}", request.getRequestURI());
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Token has been revoked\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 민감한 API 엔드포인트인지 확인
     */
    private boolean isSensitiveEndpoint(String requestUri) {
        if (sensitiveEndpoints == null) {
            sensitiveEndpoints = Arrays.asList(sensitiveEndpointsConfig.split(","));
        }

        return sensitiveEndpoints.stream()
                .anyMatch(endpoint -> {
                    String pattern = endpoint.trim();
                    if (pattern.endsWith("/**")) {
                        // /api/payments/** 패턴 처리
                        String prefix = pattern.substring(0, pattern.length() - 3);
                        return requestUri.startsWith(prefix);
                    }
                    return requestUri.startsWith(pattern);
                });
    }
}
