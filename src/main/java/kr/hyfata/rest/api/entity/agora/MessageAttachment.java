package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_attachments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "file_id"}),
        indexes = {
                @Index(name = "idx_message_attachments_message_id", columnList = "message_id"),
                @Index(name = "idx_message_attachments_file_id", columnList = "file_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private AgoraFile file;

    @Column(nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
