package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import kr.hyfata.rest.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chats",
        indexes = {
                @Index(name = "idx_chats_type", columnList = "type"),
                @Index(name = "idx_chats_context", columnList = "context"),
                @Index(name = "idx_chats_team_id", columnList = "team_id"),
                @Index(name = "idx_chats_last_message_at", columnList = "last_message_at"),
                @Index(name = "idx_chats_created_by", columnList = "created_by")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_team_group_chat", columnNames = {"team_id", "type", "context"})
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chat {

    public enum ChatType {
        DIRECT, GROUP
    }

    public enum ChatContext {
        FRIEND,  // 친구 컨텍스트 (AgoraUserProfile 사용)
        TEAM     // 팀 컨텍스트 (TeamProfile 사용)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChatContext context = ChatContext.FRIEND;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String profileImage;

    @Column(nullable = false)
    @Builder.Default
    private Long readCount = 1L;

    @Column(nullable = false)
    @Builder.Default
    private Boolean readEnabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @Column
    private LocalDateTime lastMessageAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
