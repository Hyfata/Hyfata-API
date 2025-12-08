package kr.hyfata.rest.api.dto.agora.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantProfile {
    private Long userId;
    private String displayName;
    private String profileImage;
    private String identifier;  // agoraId (FRIEND) 또는 null (TEAM)
}
