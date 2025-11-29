package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_folder_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"folder_id", "chat_id"}),
        indexes = {
                @Index(name = "idx_chat_folder_items_folder_id", columnList = "folder_id"),
                @Index(name = "idx_chat_folder_items_chat_id", columnList = "chat_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatFolderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private ChatFolder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
