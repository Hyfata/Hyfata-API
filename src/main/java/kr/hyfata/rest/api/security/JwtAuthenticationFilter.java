package kr.hyfata.rest.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hyfata.rest.api.service.TokenBlacklistService;
import kr.hyfata.rest.api.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${security.sensitive-endpoints:/api/auth/change-password,/api/users/me,/api/payments,/api/sessions}")
    private String sensitiveEndpointsConfig;

    private List<String> sensitiveEndpoints;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            final String jwt = extractJwtFromRequest(request);

            log.debug("JWT from request: {}", jwt != null ? "present" : "null");

            if (jwt == null) {
                log.debug("No JWT token in request");
            } else if (!jwtUtil.validateToken(jwt)) {
                log.warn("JWT validation failed for token");
            }

            if (jwt != null && jwtUtil.validateToken(jwt)) {
                log.debug("JWT validation passed");
                // 민감한 API인 경우 블랙리스트 확인
                if (isSensitiveEndpoint(request.getRequestURI())) {
                    String jti = jwtUtil.extractJti(jwt);
                    if (jti != null && tokenBlacklistService.isJtiBlacklisted(jti)) {
                        log.warn("Blocked request with revoked token to sensitive endpoint: {}", request.getRequestURI());
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Token has been revoked\"}");
                        return;
                    }
                }

                final String email = jwtUtil.extractEmail(jwt);
                log.debug("Extracted email from JWT: {}", email);

                final UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                log.debug("Loaded user: {}, username: {}", userDetails != null, userDetails != null ? userDetails.getUsername() : "null");

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authentication set successfully for user: {}", email);
                } else {
                    log.warn("Token validation with userDetails failed for email: {}", email);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
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
