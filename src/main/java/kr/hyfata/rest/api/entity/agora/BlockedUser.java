package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import kr.hyfata.rest.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blocked_users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "blocked_user_id"}),
        indexes = {
                @Index(name = "idx_blocked_users_user_id", columnList = "user_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
