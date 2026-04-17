package kr.hyfata.rest.api.agora.chat.dto;

import kr.hyfata.rest.api.agora.chat.entity.ChatFolder;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatFolderResponse {

    private Long id;
    private String name;
    private Integer orderIndex;
    private List<Long> chatIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ChatFolderResponse from(ChatFolder folder, List<Long> chatIds) {
        return ChatFolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .orderIndex(folder.getOrderIndex())
                .chatIds(chatIds)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}
