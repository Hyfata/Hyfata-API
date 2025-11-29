package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_roles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "name"}),
        indexes = {
                @Index(name = "idx_team_roles_team_id", columnList = "team_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String permissions;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
