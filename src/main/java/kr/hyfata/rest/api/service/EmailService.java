package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.dto.ClientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 이메일 서비스
 * <p>
 * 비동기로 이메일을 발송하여 API 응답을 지연시키지 않습니다.
 * 메일 발송 실패는 로그에만 기록되며, 비즈니스 로직에는 영향을 주지 않습니다.
 * clientId를 받아 동적으로 frontendUrl을 결정합니다 (OAuth 방식).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final ClientService clientService;

    @Value("${spring.mail.from:noreply@hyfata.com}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String defaultFrontendUrl;

    @Value("${spring.mail.enabled:true}")
    private boolean mailEnabled;

    /**
     * 2FA 코드 이메일 발송 (비동기)
     *
     * @param to 받는 사람 이메일
     * @param code 2FA 코드
     * @param clientId OAuth 클라이언트 ID
     */
    @Async
    public void sendTwoFactorEmail(String to, String code, String clientId) {
        try {
            if (!mailEnabled) {
                log.warn("Mail is disabled. Skipping 2FA email to: {}", to);
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("Your Two-Factor Authentication Code");
            message.setText("Your authentication code is: " + code + "\n\nThis code will expire in 10 minutes.");

            mailSender.send(message);
            log.info("2FA email sent successfully to: {} (client: {})", to, clientId);
        } catch (MailException e) {
            log.error("Failed to send 2FA email to {}: {}", to, e.getMessage(), e);
            // 이메일 실패는 비즈니스 로직에 영향을 주지 않음
        } catch (Exception e) {
            log.error("Unexpected error while sending 2FA email to {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * 비밀번호 재설정 이메일 발송 (비동기)
     *
     * @param to 받는 사람 이메일
     * @param resetToken 재설정 토큰
     * @param clientId OAuth 클라이언트 ID
     */
    @Async
    public void sendPasswordResetEmail(String to, String resetToken, String clientId) {
        try {
            if (!mailEnabled) {
                log.warn("Mail is disabled. Skipping password reset email to: {}", to);
                return;
            }

            String frontendUrl = getFrontendUrl(clientId);
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("Password Reset Request");
            message.setText("Click the link below to reset your password:\n\n" +
                    resetLink +
                    "\n\nThis link will expire in 1 hour.\n\n" +
                    "If you didn't request this, please ignore this email.");

            mailSender.send(message);
            log.info("Password reset email sent successfully to: {} (client: {})", to, clientId);
        } catch (MailException e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage(), e);
            // 이메일 실패는 비즈니스 로직에 영향을 주지 않음
        } catch (Exception e) {
            log.error("Unexpected error while sending password reset email to {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * 회원가입 확인 이메일 발송 (비동기)
     *
     * @param to 받는 사람 이메일
     * @param verificationToken 검증 토큰
     * @param clientId OAuth 클라이언트 ID
     */
    @Async
    public void sendEmailVerificationEmail(String to, String verificationToken, String clientId) {
        try {
            if (!mailEnabled) {
                log.warn("Mail is disabled. Skipping email verification to: {}", to);
                return;
            }

            String frontendUrl = getFrontendUrl(clientId);
            String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("Email Verification");
            message.setText("Click the link below to verify your email:\n\n" +
                    verificationLink +
                    "\n\nThis link will expire in 24 hours.");

            mailSender.send(message);
            log.info("Email verification email sent successfully to: {} (client: {})", to, clientId);
        } catch (MailException e) {
            log.error("Failed to send email verification email to {}: {}", to, e.getMessage(), e);
            // 이메일 실패는 비즈니스 로직에 영향을 주지 않음
        } catch (Exception e) {
            log.error("Unexpected error while sending email verification to {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * clientId를 기반으로 frontendUrl을 조회 (OAuth 방식)
     * @param clientId OAuth 클라이언트 ID
     * @return 클라이언트의 frontendUrl 또는 기본값
     */
    private String getFrontendUrl(String clientId) {
        return clientService.getClient(clientId)
                .map(ClientResponse::getFrontendUrl)
                .orElse(defaultFrontendUrl);
    }

}