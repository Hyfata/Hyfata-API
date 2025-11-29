package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import kr.hyfata.rest.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "user_id"}),
        indexes = {
                @Index(name = "idx_chat_participants_chat_id", columnList = "chat_id"),
                @Index(name = "idx_chat_participants_user_id", columnList = "user_id"),
                @Index(name = "idx_chat_participants_pinned", columnList = "user_id, is_pinned, pinned_at DESC")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatParticipant {

    public enum Role {
        ADMIN, MEMBER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Role role = Role.MEMBER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    @Column
    private LocalDateTime pinnedAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();
}
