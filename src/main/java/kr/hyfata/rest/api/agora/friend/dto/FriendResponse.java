package kr.hyfata.rest.api.dto.agora.friend;

import kr.hyfata.rest.api.agora.friend.entity.Friend
import kr.hyfata.rest.api.entity.agora.AgoraUserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendResponse {

    private Long friendId;

    private String agoraId;

    private String displayName;

    private String profileImage;

    private Boolean isFavorite;

    private LocalDateTime createdAt;

    public static FriendResponse from(Friend friend, AgoraUserProfile profile) {
        return FriendResponse.builder()
                .friendId(friend.getFriend().getId())
                .agoraId(profile != null ? profile.getAgoraId() : "")
                .displayName(profile != null ? profile.getDisplayName() : "")
                .profileImage(profile != null ? profile.getProfileImage() : null)
                .isFavorite(friend.getIsFavorite())
                .createdAt(friend.getCreatedAt())
                .build();
    }
}
