package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import kr.hyfata.rest.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message_read_status",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"}),
        indexes = {
                @Index(name = "idx_message_read_status_message_id", columnList = "message_id"),
                @Index(name = "idx_message_read_status_user_id", columnList = "user_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
