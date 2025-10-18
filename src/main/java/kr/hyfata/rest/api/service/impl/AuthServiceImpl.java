package kr.hyfata.rest.api.service.impl;

import kr.hyfata.rest.api.dto.*;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.service.AuthService;
import kr.hyfata.rest.api.service.ClientService;
import kr.hyfata.rest.api.service.EmailService;
import kr.hyfata.rest.api.util.JwtUtil;
import kr.hyfata.rest.api.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenGenerator tokenGenerator;
    private final EmailService emailService;
    private final ClientService clientService;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${auth.2fa.expiration-minutes:10}")
    private int twoFactorExpirationMinutes;

    @Value("${auth.reset-token.expiration-hours:1}")
    private int resetTokenExpirationHours;

    @Override
    public void register(RegisterRequest request) {
        // 클라이언트 검증
        if (clientService.validateClient(request.getClientId()).isEmpty()) {
            throw new BadCredentialsException("Invalid or disabled client");
        }

        // 입력 검증
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadCredentialsException("Passwords do not match");
        }

        // 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadCredentialsException("Email already registered");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadCredentialsException("Username already taken");
        }

        // 사용자 생성
        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .emailVerified(false)
                .emailVerificationToken(tokenGenerator.generateEmailVerificationToken())
                .build();

        userRepository.save(user);

        // 이메일 검증 링크 발송
        emailService.sendEmailVerificationEmail(user.getEmail(), user.getEmailVerificationToken(), request.getClientId());

        log.info("User registered: {} (client: {})", user.getEmail(), request.getClientId());
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        // 클라이언트 검증
        if (clientService.validateClient(request.getClientId()).isEmpty()) {
            throw new BadCredentialsException("Invalid or disabled client");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // 2FA 활성화 시
        if (user.getTwoFactorEnabled()) {
            String twoFactorCode = tokenGenerator.generate2FACode();
            user.setTwoFactorCode(twoFactorCode);
            user.setTwoFactorCodeExpiredAt(LocalDateTime.now().plusMinutes(twoFactorExpirationMinutes));
            userRepository.save(user);

            emailService.sendTwoFactorEmail(user.getEmail(), twoFactorCode, request.getClientId());

            return AuthResponse.twoFactorRequired("Please check your email for the 2FA code");
        }

        // 토큰 생성
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        log.info("User logged in: {} (client: {})", user.getEmail(), request.getClientId());

        return AuthResponse.success(accessToken, refreshToken, jwtExpiration);
    }

    @Override
    public AuthResponse verifyTwoFactor(TwoFactorRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (user.getTwoFactorCode() == null || !user.getTwoFactorCode().equals(request.getCode())) {
            throw new BadCredentialsException("Invalid 2FA code");
        }

        if (LocalDateTime.now().isAfter(user.getTwoFactorCodeExpiredAt())) {
            throw new BadCredentialsException("2FA code expired");
        }

        // 코드 정리
        user.setTwoFactorCode(null);
        user.setTwoFactorCodeExpiredAt(null);
        userRepository.save(user);

        // 토큰 생성
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        log.info("2FA verified for: {}", user.getEmail());

        return AuthResponse.success(accessToken, refreshToken, jwtExpiration);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtUtil.validateToken(request.getRefreshToken())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String email = jwtUtil.extractEmail(request.getRefreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user);

        return AuthResponse.success(newAccessToken, newRefreshToken, jwtExpiration);
    }

    @Override
    public void requestPasswordReset(String email, String clientId) {
        // 클라이언트 검증
        if (clientService.validateClient(clientId).isEmpty()) {
            throw new BadCredentialsException("Invalid or disabled client");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String resetToken = tokenGenerator.generatePasswordResetToken();
        user.setResetPasswordToken(resetToken);
        user.setResetPasswordTokenExpiredAt(LocalDateTime.now().plusHours(resetTokenExpirationHours));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), resetToken, clientId);

        log.info("Password reset requested for: {} (client: {})", email, clientId);
    }

    @Override
    public void resetPassword(PasswordResetRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadCredentialsException("Passwords do not match");
        }

        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new BadCredentialsException("Invalid reset token"));

        if (LocalDateTime.now().isAfter(user.getResetPasswordTokenExpiredAt())) {
            throw new BadCredentialsException("Reset token expired");
        }

        // 비밀번호 업데이트
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiredAt(null);
        userRepository.save(user);

        log.info("Password reset for: {}", user.getEmail());
    }

    @Override
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid verification token"));

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        log.info("Email verified for: {}", user.getEmail());
    }

    @Override
    public void enableTwoFactor(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        user.setTwoFactorEnabled(true);
        userRepository.save(user);

        log.info("2FA enabled for: {}", email);
    }

    @Override
    public void disableTwoFactor(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        user.setTwoFactorEnabled(false);
        user.setTwoFactorCode(null);
        user.setTwoFactorCodeExpiredAt(null);
        userRepository.save(user);

        log.info("2FA disabled for: {}", email);
    }
}