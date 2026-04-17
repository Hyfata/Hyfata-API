package kr.hyfata.rest.api.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.auth.dto.*;

public interface AuthService {
    /**
     * 회원가입
     */
    void register(RegisterRequest request);

    /**
     * 로그인 (세션 생성 포함)
     */
    AuthResponse login(AuthRequest request, HttpServletRequest httpRequest);

    /**
     * 2FA 검증 (세션 생성 포함)
     */
    AuthResponse verifyTwoFactor(TwoFactorRequest request, HttpServletRequest httpRequest);

    /**
     * 토큰 갱신 (세션 검증 및 갱신)
     */
    AuthResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest);

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