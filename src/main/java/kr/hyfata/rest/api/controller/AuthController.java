package kr.hyfata.rest.api.controller;

import kr.hyfata.rest.api.dto.*;
import kr.hyfata.rest.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
     * 로그인
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
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
    public ResponseEntity<AuthResponse> verifyTwoFactor(@RequestBody TwoFactorRequest request) {
        try {
            AuthResponse response = authService.verifyTwoFactor(request);
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
     * 토큰 갱신
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request);
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
}