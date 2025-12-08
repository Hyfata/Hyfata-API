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
public class ChatResponse {

    private Long chatId;
    private Chat.ChatType type;
    private Chat.ChatContext context;

    // 채팅방 표시 정보 (동적으로 결정)
    private String displayName;      // 1:1: 상대방 이름, GROUP: 채팅방 이름
    private String displayImage;     // 1:1: 상대방 이미지, GROUP: 채팅방 이미지

    // 기존 필드 (하위 호환)
    private String name;
    private String profileImage;

    // 팀 정보 (TEAM 컨텍스트인 경우)
    private Long teamId;
    private String teamName;

    // 참여자 정보
    private Long participantCount;
    private List<ParticipantProfile> participants;

    // 1:1 채팅용 상대방 프로필
    private ParticipantProfile otherParticipant;

    // 읽음 관련
    private Long readCount;
    private Boolean readEnabled;

    // 메시지 관련
    private Long messageCount;

    // 시간 정보
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
