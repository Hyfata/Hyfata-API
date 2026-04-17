package kr.hyfata.rest.api.agora.profile.dto;

import kr.hyfata.rest.api.agora.profile.entity.AgoraUserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicAgoraProfileResponse {

    private Long userId;

    private String agoraId;

    private String displayName;

    private String profileImage;

    private String bio;

    private LocalDate birthday;

    public static PublicAgoraProfileResponse from(AgoraUserProfile profile) {
        return PublicAgoraProfileResponse.builder()
                .userId(profile.getUser().getId())
                .agoraId(profile.getAgoraId())
                .displayName(profile.getDisplayName())
                .profileImage(profile.getProfileImage())
                .bio(profile.getBio())
                .birthday(profile.getBirthday())
                .build();
    }
}
