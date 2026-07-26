package kr.hyfata.rest.api.auth.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.auth.dto.AuthResponse;
import kr.hyfata.rest.api.auth.dto.PasswordResetRequest;
import kr.hyfata.rest.api.auth.dto.RegisterRequest;
import kr.hyfata.rest.api.auth.dto.TwoFactorRequest;
import kr.hyfata.rest.api.auth.service.AuthService;
import kr.hyfata.rest.api.user.User;
import kr.hyfata.rest.api.user.UserRepository;
import kr.hyfata.rest.api.client.service.ClientService;
import kr.hyfata.rest.api.infrastructure.service.EmailService;
import kr.hyfata.rest.api.session.service.SessionService;
import kr.hyfata.rest.api.infrastructure.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    /** SAS TokenSettings의 access token TTL과 동일 (15분) */
    private static final long ACCESS_TOKEN_TTL_SECONDS = 900;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final TokenGenerator tokenGenerator;
    private final EmailService emailService;
    private final ClientService clientService;
    private final SessionService sessionService;

    @Value("${auth.issuer:}")
    private String issuer;

    @Value("${auth.2fa.expiration-minutes:10}")
    private int twoFactorExpirationMinutes;

    @Value("${auth.reset-token.expiration-hours:1}")
    private int resetTokenExpirationHours;

    @Override
    public void register(RegisterRequest request) {
        // 클라이언트 검증 (clientId가 지정된 경우에만 — OAuth 가입 페이지는 클라이언트 무관)
        if (StringUtils.hasText(request.getClientId())
                && !clientService.validateClient(request.getClientId())) {
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

    /**
     * 2FA 검증 (레거시 REST 로그인 경로 전용)
     * <p>
     * Access Token은 SAS와 동일한 JwtEncoder(RS256)로 발급해 Resource Server에서 검증 가능하다.
     * Refresh Token은 세션 추적용 opaque 문자열이며, 갱신은 SAS /oauth/token 흐름만 지원한다.
     */
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

        // Access Token 발급 (SAS 토큰과 동일한 클레임 구성)
        Set<String> scopes = Set.of("profile", "email");
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS))
                .id(jti)
                .claim("email", user.getEmail())
                .claim("scope", String.join(" ", scopes))
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        // 세션 생성 (refresh token은 세션 추적용 opaque 문자열)
        String refreshToken = tokenGenerator.generatePasswordResetToken();
        sessionService.createSasSession(user, refreshToken, jti, null, null, scopes, httpRequest);

        log.info("2FA verified for: {}", user.getEmail());

        return AuthResponse.success(accessToken, refreshToken, ACCESS_TOKEN_TTL_SECONDS * 1000);
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
        // 클라이언트 검증 (clientId가 지정된 경우에만)
        if (StringUtils.hasText(clientId) && !clientService.validateClient(clientId)) {
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
