package kr.hyfata.rest.api.service.agora;

import kr.hyfata.rest.api.dto.agora.AgoraProfileResponse;
import kr.hyfata.rest.api.dto.agora.CreateAgoraProfileRequest;
import kr.hyfata.rest.api.dto.agora.UpdateAgoraProfileRequest;

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
}
