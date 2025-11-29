package kr.hyfata.rest.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_history",
        indexes = {
                @Index(name = "idx_login_history_user_id", columnList = "user_id"),
                @Index(name = "idx_login_history_created_at", columnList = "created_at DESC")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 100)
    private String location;

    @Column(length = 50)
    private String deviceType;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    private Boolean success;

    @Column(length = 100)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
