package kr.hyfata.rest.api.auth.repository;

import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    /**
     * 사용자의 활성 세션 목록 조회 (무효화되지 않고 만료되지 않은 세션)
     */
    @Query("SELECT s FROM UserSession s WHERE s.user = :user " +
            "AND s.isRevoked = false AND s.expiresAt > :now " +
            "ORDER BY s.lastActiveAt DESC")
    List<UserSession> findActiveSessionsByUser(
            @Param("user") User user,
            @Param("now") LocalDateTime now
    );

    /**
     * 사용자의 활성 세션 수
     */
    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.user = :user " +
            "AND s.isRevoked = false AND s.expiresAt > :now")
    long countActiveSessionsByUser(
            @Param("user") User user,
            @Param("now") LocalDateTime now
    );

    /**
     * 사용자의 가장 오래된 활성 세션 조회 (동시 세션 제한용)
     */
    @Query("SELECT s FROM UserSession s WHERE s.user = :user " +
            "AND s.isRevoked = false AND s.expiresAt > :now " +
            "ORDER BY s.createdAt ASC")
    List<UserSession> findOldestActiveSessionsByUser(
            @Param("user") User user,
            @Param("now") LocalDateTime now
    );

    /**
     * Refresh Token 해시로 세션 조회
     */
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    /**
     * Access Token JTI로 세션 조회
     */
    Optional<UserSession> findByAccessTokenJti(String accessTokenJti);

    /**
     * 특정 사용자의 모든 세션 무효화
     */
    @Modifying
    @Query("UPDATE UserSession s SET s.isRevoked = true WHERE s.user = :user AND s.isRevoked = false")
    int revokeAllByUser(@Param("user") User user);

    /**
     * 특정 사용자의 특정 세션 제외 모든 세션 무효화
     */
    @Modifying
    @Query("UPDATE UserSession s SET s.isRevoked = true " +
            "WHERE s.user = :user AND s.refreshTokenHash != :currentSessionHash AND s.isRevoked = false")
    int revokeOthersByUser(
            @Param("user") User user,
            @Param("currentSessionHash") String currentSessionHash
    );

    /**
     * 만료된 세션 삭제 (배치 작업용)
     */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :expiryDate")
    int deleteExpiredSessions(@Param("expiryDate") LocalDateTime expiryDate);

    /**
     * 무효화된 오래된 세션 삭제 (배치 작업용)
     */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.isRevoked = true AND s.lastActiveAt < :cutoffDate")
    int deleteOldRevokedSessions(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * 사용자 ID로 활성 세션 목록 조회
     */
    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId " +
            "AND s.isRevoked = false AND s.expiresAt > :now " +
            "ORDER BY s.lastActiveAt DESC")
    List<UserSession> findActiveSessionsByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );
}
