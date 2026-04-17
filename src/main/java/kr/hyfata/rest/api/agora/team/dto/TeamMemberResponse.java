package kr.hyfata.rest.api.agora.team.dto;

import kr.hyfata.rest.api.agora.profile.entity.AgoraUserProfile;
import kr.hyfata.rest.api.agora.team.entity.TeamMember;
import kr.hyfata.rest.api.agora.team.entity.TeamProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMemberResponse {

    private Long memberId;

    private Long userId;

    private String agoraId;

    private String displayName;

    private String profileImage;

    private String roleName;

    private LocalDateTime joinedAt;

    public static TeamMemberResponse from(TeamMember member, AgoraUserProfile agoraProfile, TeamProfile teamProfile) {
        return TeamMemberResponse.builder()
                .memberId(member.getId())
                .userId(member.getUser().getId())
                .agoraId(agoraProfile != null ? agoraProfile.getAgoraId() : "")
                .displayName(teamProfile != null ? teamProfile.getDisplayName() : null)
                .profileImage(teamProfile != null ? teamProfile.getProfileImage() : null)
                .roleName(member.getRole() != null ? member.getRole().getName() : "member")
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
