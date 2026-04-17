package kr.hyfata.rest.api.dto.agora.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {

    private Long messageId;

    private Long chatId;

    private Long senderId;

    private String senderAgoraId;

    private String senderName;

    private String senderProfileImage;

    private String content;

    private String type;

    private String eventType;

    private LocalDateTime createdAt;
}
