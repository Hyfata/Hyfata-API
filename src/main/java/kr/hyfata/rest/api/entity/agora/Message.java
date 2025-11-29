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
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_chat_id", columnList = "chat_id"),
                @Index(name = "idx_messages_sender_id", columnList = "sender_id"),
                @Index(name = "idx_messages_created_at", columnList = "created_at DESC")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    public enum MessageType {
        TEXT, IMAGE, FILE, SYSTEM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private Message replyTo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "private_only_user_id")
    private User privateOnlyUser;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MessageAttachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MessageReadStatus> readStatuses = new ArrayList<>();

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
