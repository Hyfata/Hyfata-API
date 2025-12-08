package kr.hyfata.rest.api.dto.agora.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import kr.hyfata.rest.api.entity.agora.Chat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGroupChatRequest {

    @NotBlank(message = "채팅방 이름은 필수입니다")
    private String name;

    private String profileImage;

    // 친구 그룹 채팅용 (agoraId로 멤버 지정)
    private List<String> memberAgoraIds;

    // 사용자 ID로 멤버 지정 (대안)
    private List<Long> memberUserIds;

    // 컨텍스트 (기본값: FRIEND)
    @Builder.Default
    private Chat.ChatContext context = Chat.ChatContext.FRIEND;

    // TEAM 컨텍스트인 경우 (현재는 사용 안 함 - 팀 그룹 채팅은 자동 생성)
    private Long teamId;
}
