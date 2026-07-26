package kr.hyfata.rest.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.hyfata.rest.api.auth.dto.RegisterRequest;
import kr.hyfata.rest.api.auth.service.AuthService;
import kr.hyfata.rest.api.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth 관련 페이지 컨트롤러
 * <p>
 * OAuth 2.0 프로토콜(authorize/token/revoke 등)은 Spring Authorization Server가 담당한다.
 * 이 컨트롤러는 로그인/회원가입/비밀번호 찾기 페이지와 로그아웃만 제공한다.
 * /oauth/login POST는 Spring Security formLogin이 처리한다.
 */
@Controller
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final AuthService authService;
    private final SessionService sessionService;

    /**
     * 로그인 페이지
     * GET /oauth/login?error=credentials|disabled|unverified
     * <p>
     * SAS가 /oauth/authorize 요청을 request cache에 저장해 두므로,
     * 로그인 성공 시 authorize 흐름이 자동으로 재개된다.
     */
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", switch (error) {
                case "disabled" -> "비활성화된 계정입니다.";
                case "unverified" -> "이메일 인증이 필요합니다.";
                default -> "이메일 또는 비밀번호가 올바르지 않습니다.";
            });
        }
        return "oauth/login";
    }

    /**
     * 회원가입 페이지
     * GET /oauth/register
     */
    @GetMapping("/register")
    public String registerPage() {
        return "oauth/register";
    }

    /**
     * 회원가입 처리
     * POST /oauth/register
     */
    @PostMapping("/register")
    public String register(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String username,
            Model model) {

        try {
            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setEmail(email);
            registerRequest.setPassword(password);
            registerRequest.setUsername(username);

            authService.register(registerRequest);

            log.info("User registered via OAuth page: email={}", email);

            model.addAttribute("email", email);
            return "oauth/verify-email";

        } catch (Exception e) {
            log.warn("Registration error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("username", username);
            return "oauth/register";
        }
    }

    /**
     * 비밀번호 찾기 페이지
     * GET /oauth/forgot-password
     */
    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "oauth/forgot-password";
    }

    /**
     * 비밀번호 재설정 요청 처리
     * POST /oauth/forgot-password
     */
    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        try {
            authService.requestPasswordReset(email, null);
            model.addAttribute("message", "If the email exists, a password reset link has been sent.");
            return "oauth/forgot-password";

        } catch (Exception e) {
            log.warn("Forgot password error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "oauth/forgot-password";
        }
    }

    /**
     * OAuth 로그아웃 (세션 무효화 + JTI 블랙리스트 + SAS authorization 제거)
     * POST /oauth/logout
     * <p>
     * SessionService.revokeSession이 Access Token JTI 블랙리스트 등록과
     * SAS authorization 제거(refresh token 무효화)를 함께 수행한다.
     * RFC 7009 토큰 revoke는 SAS의 /oauth/revoke가 별도 제공한다.
     */
    @PostMapping("/logout")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> logout(
            @RequestParam(required = false) String refresh_token,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication,
            HttpServletRequest request) {

        try {
            String email = authentication.getName();

            String token = refresh_token;
            if ((token == null || token.isEmpty()) && body != null) {
                token = body.get("refresh_token");
            }

            if (token == null || token.isEmpty()) {
                throw new BadCredentialsException("refresh_token is required");
            }

            // 세션 무효화 (JTI 블랙리스트 + SAS authorization 제거 포함)
            String sessionHash = sessionService.hashToken(token);
            sessionService.revokeSession(email, sessionHash, null);

            // 서버사이드 세션 무효화 (Redis)
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
                log.info("Server-side session invalidated for user: {}", email);
            }

            // SecurityContext 클리어
            SecurityContextHolder.clearContext();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Logged out successfully");

            return ResponseEntity.ok(result);

        } catch (BadCredentialsException e) {
            log.warn("Logout error: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Logout failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 에러 페이지
     */
    @GetMapping("/error")
    public String error(Model model) {
        return "oauth/error";
    }
}
