package kr.hyfata.rest.api.agora.friend.service;

import kr.hyfata.rest.api.auth.dto.agora.friend.FriendRequestResponse;
import kr.hyfata.rest.api.auth.dto.agora.friend.FriendResponse;

import java.util.List;

public interface AgoraFriendService {

    /**
     * 친구 목록 조회
     * @param userEmail 사용자 이메일
     * @return 친구 목록
     */
    List<FriendResponse> getFriendList(String userEmail);

    /**
     * 친구 요청 전송
     * @param userEmail 요청을 보내는 사용자 이메일
     * @param targetAgoraId 요청을 받을 사용자의 agoraId
     * @return 친구 요청 응답
     */
    FriendRequestResponse sendFriendRequest(String userEmail, String targetAgoraId);

    /**
     * 받은 친구 요청 목록 조회
     * @param userEmail 사용자 이메일
     * @return 받은 친구 요청 목록
     */
    List<FriendRequestResponse> getReceivedFriendRequests(String userEmail);

    /**
     * 친구 요청 수락
     * @param userEmail 사용자 이메일
     * @param requestId 친구 요청 ID
     * @return 친구 응답
     */
    FriendResponse acceptFriendRequest(String userEmail, Long requestId);

    /**
     * 친구 요청 거절
     * @param userEmail 사용자 이메일
     * @param requestId 친구 요청 ID
     * @return 성공 메시지
     */
    String rejectFriendRequest(String userEmail, Long requestId);

    /**
     * 친구 삭제
     * @param userEmail 사용자 이메일
     * @param friendId 삭제할 친구의 사용자 ID
     * @return 성공 메시지
     */
    String deleteFriend(String userEmail, Long friendId);

    /**
     * 친구 즐겨찾기 추가
     * @param userEmail 사용자 이메일
     * @param friendId 즐겨찾기할 친구의 사용자 ID
     * @return 친구 응답
     */
    FriendResponse addFavorite(String userEmail, Long friendId);

    /**
     * 친구 즐겨찾기 제거
     * @param userEmail 사용자 이메일
     * @param friendId 즐겨찾기 제거할 친구의 사용자 ID
     * @return 친구 응답
     */
    FriendResponse removeFavorite(String userEmail, Long friendId);

    /**
     * 사용자 차단
     * @param userEmail 사용자 이메일
     * @param targetUserId 차단할 사용자 ID
     * @return 성공 메시지
     */
    String blockUser(String userEmail, Long targetUserId);

    /**
     * 사용자 차단 해제
     * @param userEmail 사용자 이메일
     * @param blockedUserId 차단 해제할 사용자 ID
     * @return 성공 메시지
     */
    String unblockUser(String userEmail, Long blockedUserId);

    /**
     * 차단 목록 조회
     * @param userEmail 사용자 이메일
     * @return 차단 목록
     */
    List<FriendResponse> getBlockedUserList(String userEmail);

    /**
     * 생일 목록 조회
     * @param userEmail 사용자 이메일
     * @return 생일 예정인 친구 목록
     */
    List<FriendResponse> getFriendBirthdayList(String userEmail);
}
