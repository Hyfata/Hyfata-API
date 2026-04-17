package kr.hyfata.rest.api.agora.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadStatusDto {

    private Long chatId;

    private Long messageId;

    private Long userId;

    private String userAgoraId;

    private String eventType;
}
