package kr.hyfata.rest.api.dto.agora.friend;

import kr.hyfata.rest.api.entity.agora.FriendRequest;
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
public class FriendRequestResponse {

    private Long requestId;

    private Long fromUserId;

    private String fromAgoraId;

    private String fromDisplayName;

    private String fromProfileImage;

    private Long toUserId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static FriendRequestResponse from(FriendRequest friendRequest, AgoraUserProfile fromUserProfile) {
        return FriendRequestResponse.builder()
                .requestId(friendRequest.getId())
                .fromUserId(friendRequest.getFromUser().getId())
                .fromAgoraId(fromUserProfile != null ? fromUserProfile.getAgoraId() : "")
                .fromDisplayName(fromUserProfile != null ? fromUserProfile.getDisplayName() : "")
                .fromProfileImage(fromUserProfile != null ? fromUserProfile.getProfileImage() : null)
                .toUserId(friendRequest.getToUser().getId())
                .status(friendRequest.getStatus().toString())
                .createdAt(friendRequest.getCreatedAt())
                .updatedAt(friendRequest.getUpdatedAt())
                .build();
    }
}
