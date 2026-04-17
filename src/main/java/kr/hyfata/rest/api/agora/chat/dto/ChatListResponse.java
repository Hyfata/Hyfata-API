package kr.hyfata.rest.api.dto.agora.chat;

import kr.hyfata.rest.api.entity.agora.Chat;
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
public class ChatListResponse {

    private Long chatId;

    private String type;

    private String name;

    private String profileImage;

    private String lastMessageContent;

    private String lastMessageSenderName;

    private LocalDateTime lastMessageTime;

    private Long participantCount;

    private Boolean isPinned;

    private LocalDateTime pinnedAt;

    public static ChatListResponse from(Chat chat, Message lastMessage, Long participantCount, Boolean isPinned, LocalDateTime pinnedAt, AgoraUserProfile senderProfile) {
        String lastMessageContent = lastMessage != null ? lastMessage.getContent() : "";
        String lastMessageSenderName = lastMessage != null && senderProfile != null ? senderProfile.getDisplayName() : "";
        LocalDateTime lastMessageTime = lastMessage != null ? lastMessage.getCreatedAt() : null;

        return ChatListResponse.builder()
                .chatId(chat.getId())
                .type(chat.getType().toString())
                .name(chat.getName())
                .profileImage(chat.getProfileImage())
                .lastMessageContent(lastMessageContent)
                .lastMessageSenderName(lastMessageSenderName)
                .lastMessageTime(lastMessageTime)
                .participantCount(participantCount)
                .isPinned(isPinned)
                .pinnedAt(pinnedAt)
                .build();
    }
}
