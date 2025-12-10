package kr.hyfata.rest.api.dto.agora.team;

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
public class TeamProfileResponse {

    private Long userId;

    private String userEmail;

    private String displayName;

    private String profileImage;

    private String bio;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static TeamProfileResponse from(TeamProfile profile) {
        return TeamProfileResponse.builder()
                .userId(profile.getUser().getId())
                .userEmail(profile.getUser().getEmail())
                .displayName(profile.getDisplayName())
                .profileImage(profile.getProfileImage())
                .bio(profile.getBio())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
