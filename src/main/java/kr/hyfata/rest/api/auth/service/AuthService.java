package kr.hyfata.rest.api.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.auth.dto.AuthResponse;
import kr.hyfata.rest.api.auth.dto.PasswordResetRequest;
import kr.hyfata.rest.api.auth.dto.RegisterRequest;
import kr.hyfata.rest.api.auth.dto.TwoFactorRequest;

public interface AuthService {
    /**
     * 회원가입
     */
    void register(RegisterRequest request);

    /**
     * 2FA 검증 (레거시 REST 로그인 경로 전용)
     */
    AuthResponse verifyTwoFactor(TwoFactorRequest request, HttpServletRequest httpRequest);

    /**
     * 로그아웃 (현재 세션 무효화)
     */
    void logout(String refreshToken, String userEmail);

    /**
     * 전체 로그아웃 (모든 세션 무효화)
     */
    void logoutAll(String userEmail);

    /**
     * 비밀번호 재설정 요청
     */
    void requestPasswordReset(String email, String clientId);

    /**
     * 비밀번호 재설정
     */
    void resetPassword(PasswordResetRequest request);

    /**
     * 이메일 검증
     */
    void verifyEmail(String token);

    /**
     * 2FA 활성화
     */
    void enableTwoFactor(String email);

    /**
     * 2FA 비활성화
     */
    void disableTwoFactor(String email);
}
