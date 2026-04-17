package kr.hyfata.rest.api.dto.agora.chat;

import kr.hyfata.rest.api.entity.agora.Message;
import kr.hyfata.rest.api.entity.agora.AgoraUserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDto {

    private Long messageId;

    private Long senderId;

    private String senderAgoraId;

    private String senderName;

    private String senderProfileImage;

    private String content;

    private String type;

    private Boolean isDeleted;

    private Boolean isPinned;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static MessageDto from(Message message, AgoraUserProfile senderProfile) {
        return MessageDto.builder()
                .messageId(message.getId())
                .senderId(message.getSender().getId())
                .senderAgoraId(senderProfile != null ? senderProfile.getAgoraId() : "")
                .senderName(senderProfile != null ? senderProfile.getDisplayName() : "")
                .senderProfileImage(senderProfile != null ? senderProfile.getProfileImage() : null)
                .content(message.getContent())
                .type(message.getType().toString())
                .isDeleted(message.getIsDeleted())
                .isPinned(message.getIsPinned())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}
