package kr.hyfata.rest.api.dto.agora;

import kr.hyfata.rest.api.entity.agora.AgoraUserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgoraProfileResponse {
    private String agoraId;
    private String displayName;
    private String profileImage;
    private String bio;
    private String phone;
    private LocalDate birthday;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AgoraProfileResponse from(AgoraUserProfile profile) {
        return AgoraProfileResponse.builder()
                .agoraId(profile.getAgoraId())
                .displayName(profile.getDisplayName())
                .profileImage(profile.getProfileImage())
                .bio(profile.getBio())
                .phone(profile.getPhone())
                .birthday(profile.getBirthday())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
