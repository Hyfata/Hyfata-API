package kr.hyfata.rest.api.auth.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.auth.dto.*;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.AuthService;
import kr.hyfata.rest.api.auth.service.ClientService;
import kr.hyfata.rest.api.common.service.EmailService;
import kr.hyfata.rest.api.auth.service.SessionService;
import kr.hyfata.rest.api.common.util.JwtUtil;
import kr.hyfata.rest.api.common.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SessionService sessionService;

    @Value("${jwt.expiration:900000}")
    private long jwtExpiration;

    @Value("${auth.2fa.expiration-minutes:10}")
    private int twoFactorExpirationMinutes;

    @Value("${auth.reset-token.expiration-hours:1}")
    private int resetTokenExpirationHours;

    @Override
    public void register(RegisterRequest request) {
        // 클라이언트 검증
        if (clientService.validateClient(request.getClientId()).isEmpty()) {
            throw new BadCredentialsException("유효하지 않거나 비활성화된 클라이언트입니다.");
        }

        // 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadCredentialsException("이미 등록된 이메일입니다.");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadCredentialsException("이미 사용 중인 사용자 이름입니다.");
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
    @Transactional
    public AuthResponse login(AuthRequest request, HttpServletRequest httpRequest) {
        // 클라이언트 검증
        if (clientService.validateClient(request.getClientId()).isEmpty()) {
            throw new BadCredentialsException("Invalid or disabled client");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("비활성화된 계정입니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // 2FA 활성화 시
        if (user.getTwoFactorEnabled()) {
            String twoFactorCode = tokenGenerator.generate2FACode();
            user.setTwoFactorCode(twoFactorCode);
            user.setTwoFactorCodeExpiredAt(LocalDateTime.now().plusMinutes(twoFactorExpirationMinutes));
            userRepository.save(user);

            emailService.sendTwoFactorEmail(user.getEmail(), twoFactorCode, request.getClientId());

            return AuthResponse.twoFactorRequired("이메일로 전송된 인증 코드를 입력해 주세요.");
        }

        // 토큰 생성 (JTI 포함)
        JwtUtil.TokenResult tokenResult = jwtUtil.generateAccessTokenWithJti(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // 세션 생성
        sessionService.createSession(user, refreshToken, tokenResult.jti(), httpRequest);

        log.info("User logged in: {} (client: {})", user.getEmail(), request.getClientId());

        return AuthResponse.success(tokenResult.token(), refreshToken, jwtExpiration);
    }

    @Override
    @Transactional
    public AuthResponse verifyTwoFactor(TwoFactorRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        if (user.getTwoFactorCode() == null || !user.getTwoFactorCode().equals(request.getCode())) {
            throw new BadCredentialsException("인증 코드가 올바르지 않습니다.");
        }

        if (LocalDateTime.now().isAfter(user.getTwoFactorCodeExpiredAt())) {
            throw new BadCredentialsException("인증 코드가 만료되었습니다.");
        }

        // 코드 정리
        user.setTwoFactorCode(null);
        user.setTwoFactorCodeExpiredAt(null);
        userRepository.save(user);

        // 토큰 생성 (JTI 포함)
        JwtUtil.TokenResult tokenResult = jwtUtil.generateAccessTokenWithJti(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // 세션 생성
        sessionService.createSession(user, refreshToken, tokenResult.jti(), httpRequest);

        log.info("2FA verified for: {}", user.getEmail());

        return AuthResponse.success(tokenResult.token(), refreshToken, jwtExpiration);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        // JWT 서명 검증
        if (!jwtUtil.validateToken(request.getRefreshToken())) {
            throw new BadCredentialsException("유효하지 않은 리프레시 토큰입니다.");
        }

        // 세션 검증 (DB)
        if (!sessionService.validateSession(request.getRefreshToken())) {
            throw new BadCredentialsException("세션이 유효하지 않거나 해지되었습니다.");
        }

        String email = jwtUtil.extractEmail(request.getRefreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        // 새 토큰 생성 (토큰 로테이션)
        JwtUtil.TokenResult newTokenResult = jwtUtil.generateAccessTokenWithJti(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user);

        // 기존 세션 무효화 + 새 세션 생성
        String oldSessionHash = sessionService.hashToken(request.getRefreshToken());
        sessionService.revokeSession(email, oldSessionHash, null);
        sessionService.createSession(user, newRefreshToken, newTokenResult.jti(), httpRequest);

        log.debug("Token refreshed for: {}", email);

        return AuthResponse.success(newTokenResult.token(), newRefreshToken, jwtExpiration);
    }

    @Override
    @Transactional
    public void logout(String refreshToken, String userEmail) {
        String sessionHash = sessionService.hashToken(refreshToken);
        sessionService.revokeSession(userEmail, sessionHash, null);
        log.info("User logged out: {}", userEmail);
    }

    @Override
    @Transactional
    public void logoutAll(String userEmail) {
        sessionService.revokeAllSessions(userEmail);
        log.info("All sessions logged out for: {}", userEmail);
    }

    @Override
    public void requestPasswordReset(String email, String clientId) {
        // 클라이언트 검증
        if (clientService.validateClient(clientId).isEmpty()) {
            throw new BadCredentialsException("유효하지 않거나 비활성화된 클라이언트입니다.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        String resetToken = tokenGenerator.generatePasswordResetToken();
        user.setResetPasswordToken(resetToken);
        user.setResetPasswordTokenExpiredAt(LocalDateTime.now().plusHours(resetTokenExpirationHours));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), resetToken, clientId);

        log.info("Password reset requested for: {} (client: {})", email, clientId);
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new BadCredentialsException("유효하지 않거나 만료된 재설정 토큰입니다."));

        if (LocalDateTime.now().isAfter(user.getResetPasswordTokenExpiredAt())) {
            throw new BadCredentialsException("재설정 링크가 만료되었습니다.");
        }

        // 비밀번호 업데이트
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiredAt(null);
        userRepository.save(user);

        // 보안: 비밀번호 변경 시 모든 세션 무효화
        sessionService.revokeAllSessions(user.getEmail());

        log.info("Password reset for: {}", user.getEmail());
    }

    @Override
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadCredentialsException("유효하지 않거나 만료된 인증 토큰입니다."));

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        log.info("Email verified for: {}", user.getEmail());
    }

    @Override
    public void enableTwoFactor(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        user.setTwoFactorEnabled(true);
        userRepository.save(user);

        log.info("2FA 활성화: {}", email);
    }

    @Override
    public void disableTwoFactor(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        user.setTwoFactorEnabled(false);
        user.setTwoFactorCode(null);
        user.setTwoFactorCodeExpiredAt(null);
        userRepository.save(user);

        log.info("2FA 비활성화: {}", email);
    }
}
