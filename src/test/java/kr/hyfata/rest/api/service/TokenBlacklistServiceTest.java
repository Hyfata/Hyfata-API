package kr.hyfata.rest.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("블랙리스트에 JTI 추가 성공")
    void blacklistJti_success() {
        // given
        String jti = "test-jti-123";
        long ttl = 900L;

        // when
        blacklistService.blacklistJti(jti, ttl);

        // then
        verify(valueOperations).set(
                eq("token:blacklist:" + jti),
                eq("revoked"),
                eq(ttl),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("블랙리스트에 JTI 추가 - null/빈 값 무시")
    void blacklistJti_ignoresNullOrEmpty() {
        // when
        blacklistService.blacklistJti(null, 900L);
        blacklistService.blacklistJti("", 900L);
        blacklistService.blacklistJti("   ", 900L);

        // then
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("JTI 블랙리스트 확인 - 블랙리스트에 있는 경우")
    void isJtiBlacklisted_whenBlacklisted_returnsTrue() {
        // given
        String jti = "blacklisted-jti";
        when(redisTemplate.hasKey("token:blacklist:" + jti)).thenReturn(true);

        // when
        boolean result = blacklistService.isJtiBlacklisted(jti);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("JTI 블랙리스트 확인 - 블랙리스트에 없는 경우")
    void isJtiBlacklisted_whenNotBlacklisted_returnsFalse() {
        // given
        String jti = "valid-jti";
        when(redisTemplate.hasKey("token:blacklist:" + jti)).thenReturn(false);

        // when
        boolean result = blacklistService.isJtiBlacklisted(jti);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("JTI 블랙리스트 확인 - null/빈 값")
    void isJtiBlacklisted_nullOrEmpty_returnsFalse() {
        // when & then
        assertThat(blacklistService.isJtiBlacklisted(null)).isFalse();
        assertThat(blacklistService.isJtiBlacklisted("")).isFalse();
        assertThat(blacklistService.isJtiBlacklisted("   ")).isFalse();
    }

    @Test
    @DisplayName("토큰 블랙리스트에 추가 - 긴 토큰은 해시 처리")
    void addToBlacklist_longTokenIsHashed() {
        // given
        String longToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        long ttl = 900L;

        // when
        blacklistService.addToBlacklist(longToken, ttl);

        // then
        // 긴 토큰은 SHA-256 해시로 변환되어 저장
        verify(valueOperations).set(
                argThat(key -> key.startsWith("token:blacklist:") && key.length() == "token:blacklist:".length() + 64),
                eq("revoked"),
                eq(ttl),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("짧은 토큰/JTI는 해시 없이 그대로 사용")
    void addToBlacklist_shortTokenNotHashed() {
        // given
        String shortToken = "short-jti";
        long ttl = 900L;

        // when
        blacklistService.addToBlacklist(shortToken, ttl);

        // then
        verify(valueOperations).set(
                eq("token:blacklist:" + shortToken),
                eq("revoked"),
                eq(ttl),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("블랙리스트 확인 - Redis 오류 시 false 반환")
    void isBlacklisted_whenRedisError_returnsFalse() {
        // given
        String jti = "test-jti";
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis connection error"));

        // when
        boolean result = blacklistService.isJtiBlacklisted(jti);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("토큰 해시 생성")
    void hashToken_success() {
        // given
        String token = "test-token";

        // when
        String hash = blacklistService.hashToken(token);

        // then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters
    }
}
