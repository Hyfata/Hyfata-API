package kr.hyfata.rest.api.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hyfata.rest.api.auth.dto.*;
import kr.hyfata.rest.api.auth.service.AuthService;
import kr.hyfata.rest.api.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    /**
     * 회원가입
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        try {
            authService.register(request);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Registration successful. Please check your email to verify your account.");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Registration error: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * 로그인 (Deprecated)
     * POST /api/auth/login
     */
    @Deprecated
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            AuthResponse response = authService.login(request, httpRequest);
            response.setDeprecationWarning("This endpoint is deprecated. Please use OAuth 2.0 (/oauth/authorize) for better security.");

            // 쿠키 설정 (세션 유지)
            if (response.getAccessToken() != null) {
                setAccessTokenCookie(httpResponse, response.getAccessToken());
            }

            return ResponseEntity.ok()
                    .header("Deprecation", "true")
                    .header("Link", "</oauth/authorize>; rel=\"successor-version\"")
                    .body(response);
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * 2FA 검증
     * POST /api/auth/verify-2fa
     */
    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verifyTwoFactor(
            @RequestBody TwoFactorRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            AuthResponse response = authService.verifyTwoFactor(request, httpRequest);

            // 쿠키 설정 (세션 유지)
            if (response.getAccessToken() != null) {
                setAccessTokenCookie(httpResponse, response.getAccessToken());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("2FA verification error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * 토큰 갱신 (Deprecated)
     * POST /api/auth/refresh
     */
    @Deprecated
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            AuthResponse response = authService.refreshToken(request, httpRequest);
            response.setDeprecationWarning("This endpoint is deprecated. Please use OAuth 2.0 (/oauth/token with grant_type=refresh_token) for better security.");

            // 쿠키 갱신
            if (response.getAccessToken() != null) {
                setAccessTokenCookie(httpResponse, response.getAccessToken());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Token refresh error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * 로그아웃
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody LogoutRequest request,
            Authentication authentication,
            HttpServletResponse httpResponse
    ) {
        try {
            String email = authentication.getName();

            if (Boolean.TRUE.equals(request.getLogoutAll())) {
                authService.logoutAll(email);
            } else {
                authService.logout(request.getRefreshToken(), email);
            }

            // 쿠키 제거
            clearAccessTokenCookie(httpResponse);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Logged out successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * 비밀번호 재설정 요청
     * POST /api/auth/request-password-reset
     */
    @PostMapping("/request-password-reset")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String clientId = request.getOrDefault("clientId", "default");
            authService.requestPasswordReset(email, clientId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset link has been sent to your email");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Password reset request error: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * 비밀번호 재설정
     * POST /api/auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody PasswordResetRequest request) {
        try {
            authService.resetPassword(request);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset successful");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Password reset error: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * 이메일 검증
     * GET /api/auth/verify-email
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Email verified successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Email verification error: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * 2FA 활성화
     * POST /api/auth/enable-2fa
     */
    @PostMapping("/enable-2fa")
    public ResponseEntity<Map<String, String>> enableTwoFactor(Authentication authentication) {
        try {
            String email = authentication.getName();
            authService.enableTwoFactor(email);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Two-factor authentication enabled");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Enable 2FA error: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * 2FA 비활성화
     * POST /api/auth/disable-2fa
     */
    @PostMapping("/disable-2fa")
    public ResponseEntity<Map<String, String>> disableTwoFactor(Authentication authentication) {
        try {
            String email = authentication.getName();
            authService.disableTwoFactor(email);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Two-factor authentication disabled");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Disable 2FA error: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // ========== 유틸리티 메서드 ==========

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        long maxAge = jwtUtil.getJwtExpiration() / 1000;
        ResponseCookie cookie = ResponseCookie.from("hyfata_access_token", accessToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("hyfata_access_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
