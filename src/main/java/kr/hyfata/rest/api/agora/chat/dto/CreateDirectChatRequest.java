package kr.hyfata.rest.api.dto.agora.chat;

import jakarta.validation.constraints.NotNull;
import kr.hyfata.rest.api.entity.agora.Chat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDirectChatRequest {

    @NotNull(message = "대상 사용자 ID는 필수입니다")
    private Long targetUserId;

    @NotNull(message = "채팅 컨텍스트는 필수입니다")
    private Chat.ChatContext context;

    // TEAM 컨텍스트인 경우 필수
    private Long teamId;
}
