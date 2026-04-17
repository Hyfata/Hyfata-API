package kr.hyfata.rest.api.dto.agora.chat;

import kr.hyfata.rest.api.entity.agora.Chat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupChatResponse {

    private Long chatId;

    private String name;

    private String profileImage;

    private Long memberCount;

    private List<String> memberAgoraIds;

    private String creatorAgoraId;

    private LocalDateTime createdAt;

    public static GroupChatResponse from(Chat chat, List<String> memberAgoraIds, String creatorAgoraId) {
        return GroupChatResponse.builder()
                .chatId(chat.getId())
                .name(chat.getName())
                .profileImage(chat.getProfileImage())
                .memberCount((long) chat.getParticipants().size())
                .memberAgoraIds(memberAgoraIds)
                .creatorAgoraId(creatorAgoraId)
                .createdAt(chat.getCreatedAt())
                .build();
    }
}
