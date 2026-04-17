package kr.hyfata.rest.api.agora.profile.service;

import kr.hyfata.rest.api.auth.dto.agora.AgoraProfileResponse;
import kr.hyfata.rest.api.auth.dto.agora.CreateAgoraProfileRequest;
import kr.hyfata.rest.api.auth.dto.agora.PublicAgoraProfileResponse;
import kr.hyfata.rest.api.auth.dto.agora.UpdateAgoraProfileRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AgoraProfileService {

    /**
     * 내 Agora 프로필 조회
     * @param userEmail 사용자 이메일
     * @return Agora 프로필 (없으면 null)
     */
    AgoraProfileResponse getMyProfile(String userEmail);

    /**
     * Agora 프로필 생성
     * @param userEmail 사용자 이메일
     * @param request 생성 요청
     * @return 생성된 프로필
     */
    AgoraProfileResponse createProfile(String userEmail, CreateAgoraProfileRequest request);

    /**
     * Agora 프로필 수정
     * @param userEmail 사용자 이메일
     * @param request 수정 요청
     * @return 수정된 프로필
     */
    AgoraProfileResponse updateProfile(String userEmail, UpdateAgoraProfileRequest request);

    /**
     * 타 사용자 프로필 조회 (공개 정보)
     * @param agoraId 조회할 사용자의 agoraId
     * @return 공개 프로필
     */
    PublicAgoraProfileResponse getUserProfile(String agoraId);

    /**
     * 사용자 검색
     * @param keyword 검색 키워드 (agoraId 또는 displayName)
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    Page<PublicAgoraProfileResponse> searchUsers(String keyword, Pageable pageable);

    /**
     * 프로필 이미지 변경
     * @param userEmail 사용자 이메일
     * @param imageUrl 새 이미지 URL
     * @return 수정된 프로필
     */
    AgoraProfileResponse updateProfileImage(String userEmail, String imageUrl);

    /**
     * agoraId 중복 확인
     * @param agoraId 확인할 agoraId
     * @return 중복 여부
     */
    boolean checkAgoraIdExists(String agoraId);
}
