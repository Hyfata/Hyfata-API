package kr.hyfata.rest.api.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.dto.UserSessionDTO;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.UserSession;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.repository.UserSessionRepository;
import kr.hyfata.rest.api.service.SessionService;
import kr.hyfata.rest.api.service.TokenBlacklistService;
import kr.hyfata.rest.api.util.DeviceDetector;
import kr.hyfata.rest.api.util.GeoIpService;
import kr.hyfata.rest.api.util.IpUtil;
import kr.hyfata.rest.api.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final TokenBlacklistService blacklistService;
    private final IpUtil ipUtil;
    private final DeviceDetector deviceDetector;
    private final GeoIpService geoIpService;
    private final JwtUtil jwtUtil;

    @Value("${session.max-per-user:5}")
    private int maxSessionsPerUser;

    @Value("${jwt.refresh-expiration:1209600000}")
    private long refreshTokenExpiration;

    @Override
    @Transactional
    public UserSession createSession(User user, String refreshToken, String accessTokenJti,
                                      HttpServletRequest request) {
        return createSession(user, refreshToken, accessTokenJti, request, false);
    }

    @Override
    @Transactional
    public UserSession createSession(User user, String refreshToken, String accessTokenJti,
                                      HttpServletRequest request, boolean isPkceFlow) {
        // 동시 세션 수 확인 및 제한
        enforceSessionLimit(user);

        String tokenHash = hashToken(refreshToken);
        String ipAddress = ipUtil.normalizeIp(ipUtil.getClientIp(request));
        String userAgent = request.getHeader("User-Agent");
        DeviceDetector.DeviceInfo deviceInfo = deviceDetector.parse(userAgent);
        String location = geoIpService.resolveLocation(ipAddress);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(refreshTokenExpiration / 1000);

        UserSession session = UserSession.builder()
                .refreshTokenHash(tokenHash)
                .user(user)
                .accessTokenJti(accessTokenJti)
                .deviceType(deviceInfo.getDeviceType())
                .deviceName(deviceInfo.getDeviceName())
                .ipAddress(ipAddress)
                .location(location)
                .userAgent(userAgent)
                .expiresAt(expiresAt)
                .isRevoked(false)
                .pkceFlow(isPkceFlow)  // PKCE 기반 Public Client 여부
                .lastActiveAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        return sessionRepository.save(session);
    }

    /**
     * 동시 세션 수 제한 적용
     */
    private void enforceSessionLimit(User user) {
        long activeCount = sessionRepository.countActiveSessionsByUser(user, LocalDateTime.now());

        if (activeCount >= maxSessionsPerUser) {
            // 가장 오래된 세션을 무효화
            List<UserSession> oldestSessions = sessionRepository
                    .findOldestActiveSessionsByUser(user, LocalDateTime.now());

            if (!oldestSessions.isEmpty()) {
                UserSession oldest = oldestSessions.get(0);
                oldest.revoke();

                // 해당 세션의 Access Token도 블랙리스트에 추가
                if (oldest.getAccessTokenJti() != null) {
                    blacklistService.blacklistJti(
                            oldest.getAccessTokenJti(),
                            jwtUtil.getJwtExpiration() / 1000
                    );
                }

                sessionRepository.save(oldest);
                log.info("Session limit exceeded. Revoked oldest session for user: {}", user.getEmail());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSessionDTO> getActiveSessions(String userEmail, String currentRefreshToken) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String currentHash = currentRefreshToken != null ? hashToken(currentRefreshToken) : null;

        List<UserSession> sessions = sessionRepository
                .findActiveSessionsByUser(user, LocalDateTime.now());

        return sessions.stream()
                .map(session -> toDTO(session, currentHash))
                .collect(Collectors.toList());
    }

    private UserSessionDTO toDTO(UserSession session, String currentHash) {
        boolean isCurrent = currentHash != null &&
                currentHash.equals(session.getRefreshTokenHash());

        return UserSessionDTO.builder()
                .sessionId(session.getRefreshTokenHash())
                .deviceType(session.getDeviceType())
                .deviceName(session.getDeviceName())
                .ipAddress(session.getIpAddress())
                .location(session.getLocation())
                .lastActiveAt(session.getLastActiveAt())
                .createdAt(session.getCreatedAt())
                .expiresAt(session.getExpiresAt())
                .isCurrent(isCurrent)
                .build();
    }

    @Override
    @Transactional
    public void revokeSession(String userEmail, String sessionId, String currentAccessToken) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        UserSession session = sessionRepository.findByRefreshTokenHash(sessionId)
                .orElseThrow(() -> new BadCredentialsException("Session not found"));

        // 본인의 세션인지 확인
        if (!session.getUser().getId().equals(user.getId())) {
            throw new BadCredentialsException("Cannot revoke another user's session");
        }

        session.revoke();
        sessionRepository.save(session);

        // 해당 세션의 Access Token 블랙리스트 등록
        if (session.getAccessTokenJti() != null) {
            blacklistService.blacklistJti(
                    session.getAccessTokenJti(),
                    jwtUtil.getJwtExpiration() / 1000
            );
        }

        log.info("Session revoked: {} for user: {}", sessionId, userEmail);
    }

    @Override
    @Transactional
    public void revokeAllSessions(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // 모든 활성 세션의 Access Token을 블랙리스트에 추가
        List<UserSession> activeSessions = sessionRepository
                .findActiveSessionsByUser(user, LocalDateTime.now());

        for (UserSession session : activeSessions) {
            if (session.getAccessTokenJti() != null) {
                blacklistService.blacklistJti(
                        session.getAccessTokenJti(),
                        jwtUtil.getJwtExpiration() / 1000
                );
            }
        }

        int revokedCount = sessionRepository.revokeAllByUser(user);
        log.info("All sessions revoked for user: {}. Count: {}", userEmail, revokedCount);
    }

    @Override
    @Transactional
    public void revokeOtherSessions(String userEmail, String currentRefreshToken) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String currentHash = hashToken(currentRefreshToken);

        // 현재 세션 제외 다른 세션의 Access Token을 블랙리스트에 추가
        List<UserSession> activeSessions = sessionRepository
                .findActiveSessionsByUser(user, LocalDateTime.now());

        for (UserSession session : activeSessions) {
            if (!session.getRefreshTokenHash().equals(currentHash) &&
                    session.getAccessTokenJti() != null) {
                blacklistService.blacklistJti(
                        session.getAccessTokenJti(),
                        jwtUtil.getJwtExpiration() / 1000
                );
            }
        }

        int revokedCount = sessionRepository.revokeOthersByUser(user, currentHash);
        log.info("Other sessions revoked for user: {}. Count: {}", userEmail, revokedCount);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateSession(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        Optional<UserSession> sessionOpt = sessionRepository.findByRefreshTokenHash(tokenHash);

        if (sessionOpt.isEmpty()) {
            log.debug("Session not found for token hash");
            return false;
        }

        UserSession session = sessionOpt.get();
        boolean valid = session.isValid();

        if (!valid) {
            log.debug("Session is not valid: revoked={}, expires={}",
                    session.getIsRevoked(), session.getExpiresAt());
        }

        return valid;
    }

    @Override
    @Transactional
    public void updateSessionActivity(String refreshToken, String newAccessTokenJti) {
        String tokenHash = hashToken(refreshToken);
        Optional<UserSession> sessionOpt = sessionRepository.findByRefreshTokenHash(tokenHash);

        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.updateActivity();
            session.setAccessTokenJti(newAccessTokenJti);
            sessionRepository.save(session);
        }
    }

    @Override
    public String hashToken(String token) {
        return DigestUtils.sha256Hex(token);
    }
}
