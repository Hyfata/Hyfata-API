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
                @Index(name = "idx_user_sessions_last_active_at", columnList = "last_active_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @Column(length = 255)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 50)
    private String deviceType;

    @Column(length = 100)
    private String deviceName;

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 100)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
