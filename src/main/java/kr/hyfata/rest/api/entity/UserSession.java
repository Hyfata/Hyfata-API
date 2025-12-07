package kr.hyfata.rest.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions",
        indexes = {
                @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_user_sessions_last_active_at", columnList = "last_active_at"),
                @Index(name = "idx_user_sessions_expires_at", columnList = "expires_at"),
                @Index(name = "idx_user_sessions_is_revoked", columnList = "is_revoked")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "access_token_jti", length = 64)
    private String accessTokenJti;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "ip_address", length = 50, nullable = false)
    private String ipAddress;

    @Column(length = 100)
    private String location;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private Boolean isRevoked = false;

    @Column(name = "pkce_flow", nullable = false)
    @Builder.Default
    private Boolean pkceFlow = false;  // PKCE 기반 Public Client 여부

    @Column(name = "last_active_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 세션이 유효한지 확인
     */
    public boolean isValid() {
        return !isRevoked && expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * 세션 활동 시간 갱신
     */
    public void updateActivity() {
        this.lastActiveAt = LocalDateTime.now();
    }

    /**
     * 세션 무효화
     */
    public void revoke() {
        this.isRevoked = true;
    }
}
