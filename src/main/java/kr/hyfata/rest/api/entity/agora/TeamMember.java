package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import kr.hyfata.rest.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "user_id"}),
        indexes = {
                @Index(name = "idx_team_members_team_id", columnList = "team_id"),
                @Index(name = "idx_team_members_user_id", columnList = "user_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private TeamRole role;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();
}
