package kr.hyfata.rest.api.dto.agora.team;

import kr.hyfata.rest.api.entity.agora.AgoraUserProfile;
import kr.hyfata.rest.api.entity.agora.TeamInvitation;
import kr.hyfata.rest.api.entity.agora.TeamProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamInvitationResponse {

    private Long invitationId;

    private Long teamId;

    private String teamName;

    private String teamProfileImage;

    // 초대한 사람 정보 (TeamProfile 사용)
    private String fromAgoraId;

    private String fromDisplayName;

    private String fromProfileImage;

    // 초대받은 사람 정보 (TeamProfile 사용)
    private String toAgoraId;

    private String toDisplayName;

    private String toProfileImage;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static TeamInvitationResponse from(
            TeamInvitation invitation,
            AgoraUserProfile fromAgoraProfile,
            TeamProfile fromTeamProfile,
            AgoraUserProfile toAgoraProfile,
            TeamProfile toTeamProfile
    ) {
        return TeamInvitationResponse.builder()
                .invitationId(invitation.getId())
                .teamId(invitation.getTeam().getId())
                .teamName(invitation.getTeam().getName())
                .teamProfileImage(invitation.getTeam().getProfileImage())
                .fromAgoraId(fromAgoraProfile != null ? fromAgoraProfile.getAgoraId() : "")
                .fromDisplayName(fromTeamProfile != null ? fromTeamProfile.getDisplayName() : null)
                .fromProfileImage(fromTeamProfile != null ? fromTeamProfile.getProfileImage() : null)
                .toAgoraId(toAgoraProfile != null ? toAgoraProfile.getAgoraId() : "")
                .toDisplayName(toTeamProfile != null ? toTeamProfile.getDisplayName() : null)
                .toProfileImage(toTeamProfile != null ? toTeamProfile.getProfileImage() : null)
                .status(invitation.getStatus().toString())
                .createdAt(invitation.getCreatedAt())
                .updatedAt(invitation.getUpdatedAt())
                .build();
    }
}
