package kr.hyfata.rest.api.auth.controller;

import kr.hyfata.rest.api.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PageController {

    private final AuthService authService;

    /**
     * 브라우저용 이메일 인증 페이지
     * GET /verify-email?token=xxx
     */
    @GetMapping("/verify-email")
    public String verifyEmailPage(@RequestParam String token, Model model) {
        try {
            authService.verifyEmail(token);
            model.addAttribute("success", true);
            model.addAttribute("message", "이메일 인증이 완료되었습니다. 이제 로그인할 수 있습니다.");
            log.info("이메일 인증 완료 (브라우저): token={}", token);
        } catch (Exception e) {
            model.addAttribute("success", false);
            model.addAttribute("message", "인증에 실패했습니다: " + e.getMessage());
            log.warn("이메일 인증 실패 (브라우저): token={}, error={}", token, e.getMessage());
        }
        return "verify-result";
    }
}
