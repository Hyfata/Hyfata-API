package kr.hyfata.rest.api.scheduler;

import kr.hyfata.rest.api.repository.AuthorizationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * OAuth 만료된 Authorization Code 정리
 * 만료된 인증 코드를 정기적으로 삭제
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuthCleanupScheduler {

    private final AuthorizationCodeRepository authorizationCodeRepository;

    /**
     * 만료된 Authorization Code 정리
     * 매 시간마다 실행 (1시간 = 3600000ms)
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredAuthorizationCodes() {
        try {
            LocalDateTime now = LocalDateTime.now();
            authorizationCodeRepository.deleteByExpiresAtBefore(now);
            log.info("✅ Expired authorization codes cleaned up");
        } catch (Exception e) {
            log.error("❌ Error cleaning up expired authorization codes: {}", e.getMessage(), e);
        }
    }
}
