package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.dto.*;

public interface AuthService {
    /**
     * 회원가입
     */
    void register(RegisterRequest request);

    /**
     * 로그인
     */
    AuthResponse login(AuthRequest request);

    /**
     * 2FA 검증
     */
    AuthResponse verifyTwoFactor(TwoFactorRequest request);

    /**
     * 토큰 갱신
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * 비밀번호 재설정 요청
     */
    void requestPasswordReset(String email);

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