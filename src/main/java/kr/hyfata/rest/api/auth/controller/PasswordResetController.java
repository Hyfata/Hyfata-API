package kr.hyfata.rest.api.auth.controller;

import kr.hyfata.rest.api.auth.dto.PasswordResetRequest;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final AuthService authService;
    private final UserRepository userRepository;

    /**
     * 브라우저용 비밀번호 재설정 페이지
     * GET /reset-password?token=xxx
     */
    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam String token, Model model) {
        try {
            userRepository.findByResetPasswordToken(token)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

            model.addAttribute("token", token);
            return "reset-password";
        } catch (Exception e) {
            log.warn("Reset password page error: token={}, error={}", token, e.getMessage());
            model.addAttribute("success", false);
            model.addAttribute("message", "유효하지 않거나 만료된 재설정 링크입니다.");
            return "reset-result";
        }
    }

    /**
     * 브라우저용 비밀번호 재설정 처리
     * POST /reset-password
     */
    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model model) {

        try {
            PasswordResetRequest request = PasswordResetRequest.builder()
                    .token(token)
                    .newPassword(newPassword)
                    .confirmPassword(confirmPassword)
                    .build();

            authService.resetPassword(request);

            model.addAttribute("success", true);
            model.addAttribute("message", "비밀번호가 성공적으로 변경되었습니다.");
            log.info("Password reset successful via browser page");
        } catch (Exception e) {
            log.warn("Reset password error: {}", e.getMessage());
            model.addAttribute("success", false);
            model.addAttribute("message", "비밀번호 변경에 실패했습니다: " + e.getMessage());
        }

        return "reset-result";
    }
}
