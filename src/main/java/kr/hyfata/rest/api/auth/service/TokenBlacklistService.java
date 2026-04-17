package kr.hyfata.rest.api.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 토큰 블랙리스트 서비스
 * 민감한 API 요청 시 무효화된 Access Token을 차단
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 토큰을 블랙리스트에 추가
     * @param tokenOrJti 토큰 또는 JTI
     * @param ttlSeconds TTL (초)
     */
    public void addToBlacklist(String tokenOrJti, long ttlSeconds) {
        if (tokenOrJti == null || tokenOrJti.isBlank()) {
            return;
        }

        try {
            String key = BLACKLIST_PREFIX + hashIfNeeded(tokenOrJti);
            redisTemplate.opsForValue().set(key, "revoked", ttlSeconds, TimeUnit.SECONDS);
            log.debug("Token added to blacklist: {}", key);
        } catch (Exception e) {
            log.error("Failed to add token to blacklist: {}", e.getMessage());
        }
    }

    /**
     * JTI를 블랙리스트에 추가 (Access Token 무효화)
     * @param jti JWT ID
     * @param ttlSeconds TTL (초)
     */
    public void blacklistJti(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }

        try {
            String key = BLACKLIST_PREFIX + jti;
            redisTemplate.opsForValue().set(key, "revoked", ttlSeconds, TimeUnit.SECONDS);
            log.debug("JTI added to blacklist: {}", jti);
        } catch (Exception e) {
            log.error("Failed to blacklist JTI: {}", e.getMessage());
        }
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     * @param tokenOrJti 토큰 또는 JTI
     * @return 블랙리스트에 있으면 true
     */
    public boolean isBlacklisted(String tokenOrJti) {
        if (tokenOrJti == null || tokenOrJti.isBlank()) {
            return false;
        }

        try {
            String key = BLACKLIST_PREFIX + hashIfNeeded(tokenOrJti);
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Failed to check blacklist: {}", e.getMessage());
            // Redis 오류 시 보안을 위해 false 반환 (기본 JWT 검증만 수행)
            return false;
        }
    }

    /**
     * JTI가 블랙리스트에 있는지 확인
     * @param jti JWT ID
     * @return 블랙리스트에 있으면 true
     */
    public boolean isJtiBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }

        try {
            String key = BLACKLIST_PREFIX + jti;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Failed to check JTI blacklist: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 블랙리스트에서 토큰 제거 (필요시)
     */
    public void removeFromBlacklist(String tokenOrJti) {
        if (tokenOrJti == null || tokenOrJti.isBlank()) {
            return;
        }

        try {
            String key = BLACKLIST_PREFIX + hashIfNeeded(tokenOrJti);
            redisTemplate.delete(key);
            log.debug("Token removed from blacklist: {}", key);
        } catch (Exception e) {
            log.error("Failed to remove token from blacklist: {}", e.getMessage());
        }
    }

    /**
     * JWT 토큰인 경우 해시 처리, JTI인 경우 그대로 반환
     */
    private String hashIfNeeded(String value) {
        // JWT 토큰은 보통 100자 이상, JTI는 32자
        if (value.length() > 50) {
            return DigestUtils.sha256Hex(value);
        }
        return value;
    }

    /**
     * 토큰을 SHA-256으로 해시
     */
    public String hashToken(String token) {
        return DigestUtils.sha256Hex(token);
    }
}
