package kr.hyfata.rest.api.agora.friend.service.impl;

import kr.hyfata.rest.api.agora.friend.dto.FriendRequestResponse;
import kr.hyfata.rest.api.agora.friend.dto.FriendResponse;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.agora.profile.entity.AgoraUserProfile;
import kr.hyfata.rest.api.agora.friend.entity.BlockedUser;
import kr.hyfata.rest.api.agora.friend.entity.Friend;
import kr.hyfata.rest.api.agora.friend.entity.FriendRequest;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.agora.profile.repository.AgoraUserProfileRepository;
import kr.hyfata.rest.api.agora.friend.repository.BlockedUserRepository;
import kr.hyfata.rest.api.agora.friend.repository.FriendRepository;
import kr.hyfata.rest.api.agora.friend.repository.FriendRequestRepository;
import kr.hyfata.rest.api.agora.friend.service.AgoraFriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraFriendServiceImpl implements AgoraFriendService {

    private final UserRepository userRepository;
    private final AgoraUserProfileRepository agoraUserProfileRepository;
    private final FriendRepository friendRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final BlockedUserRepository blockedUserRepository;

    @Override
    public List<FriendResponse> getFriendList(String userEmail) {
        User user = findUserByEmail(userEmail);
        return friendRepository.findByUser_IdOrderByIsFavoriteDescCreatedAtDesc(user.getId())
                .stream()
                .map(friend -> {
                    AgoraUserProfile profile = agoraUserProfileRepository.findById(friend.getFriend().getId())
                            .orElse(null);
                    return FriendResponse.from(friend, profile);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FriendRequestResponse sendFriendRequest(String userEmail, String targetAgoraId) {
        User fromUser = findUserByEmail(userEmail);

        AgoraUserProfile targetProfile = agoraUserProfileRepository.findByAgoraId(targetAgoraId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with agoraId: " + targetAgoraId));

        User toUser = targetProfile.getUser();

        // Check if already friends
        if (friendRepository.existsByUser_IdAndFriend_Id(fromUser.getId(), toUser.getId())) {
            throw new IllegalStateException("Already friends with this user");
        }

        // Check if already have a pending request
        if (friendRequestRepository.existsByFromUser_IdAndToUser_IdAndStatus(
                fromUser.getId(), toUser.getId(), FriendRequest.Status.PENDING)) {
            throw new IllegalStateException("Friend request already sent");
        }

        // Check if user is blocked
        if (blockedUserRepository.existsByUser_IdAndBlockedUser_Id(fromUser.getId(), toUser.getId())) {
            throw new IllegalStateException("Cannot send friend request to blocked user");
        }

        FriendRequest friendRequest = FriendRequest.builder()
                .fromUser(fromUser)
                .toUser(toUser)
                .status(FriendRequest.Status.PENDING)
                .build();

        FriendRequest saved = friendRequestRepository.save(friendRequest);
        AgoraUserProfile fromUserProfile = agoraUserProfileRepository.findById(fromUser.getId())
                .orElse(null);
        return FriendRequestResponse.from(saved, fromUserProfile);
    }

    @Override
    public List<FriendRequestResponse> getReceivedFriendRequests(String userEmail) {
        User user = findUserByEmail(userEmail);
        return friendRequestRepository.findByToUser_IdAndStatusOrderByCreatedAtDesc(
                user.getId(), FriendRequest.Status.PENDING)
                .stream()
                .map(friendRequest -> {
                    AgoraUserProfile fromUserProfile = agoraUserProfileRepository.findById(friendRequest.getFromUser().getId())
                            .orElse(null);
                    return FriendRequestResponse.from(friendRequest, fromUserProfile);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FriendResponse acceptFriendRequest(String userEmail, Long requestId) {
        User toUser = findUserByEmail(userEmail);

        FriendRequest friendRequest = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        // Verify this request is for the current user
        if (!friendRequest.getToUser().getId().equals(toUser.getId())) {
            throw new IllegalStateException("You cannot accept this friend request");
        }

        // Update request status
        friendRequest.setStatus(FriendRequest.Status.ACCEPTED);
        friendRequestRepository.save(friendRequest);

        User fromUser = friendRequest.getFromUser();

        // Create bidirectional friendship
        Friend friend1 = Friend.builder()
                .user(toUser)
                .friend(fromUser)
                .isFavorite(false)
                .build();

        Friend friend2 = Friend.builder()
                .user(fromUser)
                .friend(toUser)
                .isFavorite(false)
                .build();

        friendRepository.save(friend1);
        friendRepository.save(friend2);

        AgoraUserProfile fromUserProfile = agoraUserProfileRepository.findById(fromUser.getId())
                .orElse(null);
        return FriendResponse.from(friend1, fromUserProfile);
    }

    @Override
    @Transactional
    public String rejectFriendRequest(String userEmail, Long requestId) {
        User toUser = findUserByEmail(userEmail);

        FriendRequest friendRequest = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        // Verify this request is for the current user
        if (!friendRequest.getToUser().getId().equals(toUser.getId())) {
            throw new IllegalStateException("You cannot reject this friend request");
        }

        friendRequest.setStatus(FriendRequest.Status.REJECTED);
        friendRequestRepository.save(friendRequest);

        return "Friend request rejected";
    }

    @Override
    @Transactional
    public String deleteFriend(String userEmail, Long friendId) {
        User user = findUserByEmail(userEmail);

        // Delete both directions of friendship
        friendRepository.deleteByUser_IdAndFriend_Id(user.getId(), friendId);
        friendRepository.deleteByUser_IdAndFriend_Id(friendId, user.getId());

        return "Friend deleted";
    }

    @Override
    @Transactional
    public FriendResponse addFavorite(String userEmail, Long friendId) {
        User user = findUserByEmail(userEmail);

        Friend friend = friendRepository.findByUser_IdAndFriend_Id(user.getId(), friendId)
                .orElseThrow(() -> new IllegalArgumentException("Friend not found"));

        friend.setIsFavorite(true);
        friendRepository.save(friend);

        AgoraUserProfile friendProfile = agoraUserProfileRepository.findById(friendId)
                .orElse(null);
        return FriendResponse.from(friend, friendProfile);
    }

    @Override
    @Transactional
    public FriendResponse removeFavorite(String userEmail, Long friendId) {
        User user = findUserByEmail(userEmail);

        Friend friend = friendRepository.findByUser_IdAndFriend_Id(user.getId(), friendId)
                .orElseThrow(() -> new IllegalArgumentException("Friend not found"));

        friend.setIsFavorite(false);
        friendRepository.save(friend);

        AgoraUserProfile friendProfile = agoraUserProfileRepository.findById(friendId)
                .orElse(null);
        return FriendResponse.from(friend, friendProfile);
    }

    @Override
    @Transactional
    public String blockUser(String userEmail, Long targetUserId) {
        User user = findUserByEmail(userEmail);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if already blocked
        if (blockedUserRepository.existsByUser_IdAndBlockedUser_Id(user.getId(), targetUserId)) {
            throw new IllegalStateException("User already blocked");
        }

        // If they are friends, delete the friendship
        friendRepository.deleteByUser_IdAndFriend_Id(user.getId(), targetUserId);
        friendRepository.deleteByUser_IdAndFriend_Id(targetUserId, user.getId());

        BlockedUser blockedUser = BlockedUser.builder()
                .user(user)
                .blockedUser(targetUser)
                .build();

        blockedUserRepository.save(blockedUser);
        return "User blocked";
    }

    @Override
    @Transactional
    public String unblockUser(String userEmail, Long blockedUserId) {
        User user = findUserByEmail(userEmail);

        blockedUserRepository.deleteByUser_IdAndBlockedUser_Id(user.getId(), blockedUserId);
        return "User unblocked";
    }

    @Override
    public List<FriendResponse> getBlockedUserList(String userEmail) {
        User user = findUserByEmail(userEmail);

        return blockedUserRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(blockedUser -> {
                    Long blockedUserId = blockedUser.getBlockedUser().getId();
                    AgoraUserProfile profile = agoraUserProfileRepository.findById(blockedUserId)
                            .orElse(null);
                    return FriendResponse.builder()
                            .friendId(blockedUserId)
                            .agoraId(profile != null ? profile.getAgoraId() : "")
                            .displayName(profile != null ? profile.getDisplayName() : "")
                            .profileImage(profile != null ? profile.getProfileImage() : null)
                            .isFavorite(false)
                            .createdAt(blockedUser.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<FriendResponse> getFriendBirthdayList(String userEmail) {
        User user = findUserByEmail(userEmail);
        LocalDateTime now = LocalDateTime.now();
        Month currentMonth = now.getMonth();
        int currentDay = now.getDayOfMonth();

        return friendRepository.findByUser_IdOrderByIsFavoriteDescCreatedAtDesc(user.getId())
                .stream()
                .filter(friend -> {
                    AgoraUserProfile profile = agoraUserProfileRepository.findById(friend.getFriend().getId())
                            .orElse(null);
                    if (profile == null || profile.getBirthday() == null) {
                        return false;
                    }
                    Month birthMonth = profile.getBirthday().getMonth();
                    int birthDay = profile.getBirthday().getDayOfMonth();
                    // Check if birthday is today or in the next 7 days
                    if (birthMonth == currentMonth) {
                        return birthDay >= currentDay && birthDay < currentDay + 7;
                    } else if (birthMonth == currentMonth.plus(1)) {
                        int daysInMonth = currentMonth.length(false);
                        return birthDay < (7 - (daysInMonth - currentDay));
                    }
                    return false;
                })
                .map(friend -> {
                    AgoraUserProfile profile = agoraUserProfileRepository.findById(friend.getFriend().getId())
                            .orElse(null);
                    return FriendResponse.from(friend, profile);
                })
                .collect(Collectors.toList());
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
