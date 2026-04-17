package kr.hyfata.rest.api.dto.agora.team;

import kr.hyfata.rest.api.entity.agora.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamResponse {

    private Long teamId;

    private String name;

    private String description;

    private String profileImage;

    private Boolean isMain;

    private String creatorEmail;

    private Long memberCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static TeamResponse from(Team team) {
        return TeamResponse.builder()
                .teamId(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .profileImage(team.getProfileImage())
                .isMain(team.getIsMain())
                .creatorEmail(team.getCreatedBy().getEmail())
                .memberCount((long) team.getMembers().size())
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }
}
