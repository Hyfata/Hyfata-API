package kr.hyfata.rest.api.service.agora;

import kr.hyfata.rest.api.dto.agora.team.TeamProfileResponse;
import kr.hyfata.rest.api.dto.agora.team.CreateTeamProfileRequest;

public interface AgoraTeamProfileService {

    TeamProfileResponse getMyTeamProfile(String userEmail);

    TeamProfileResponse createTeamProfile(String userEmail, CreateTeamProfileRequest request);

    TeamProfileResponse updateTeamProfile(String userEmail, String displayName, String profileImage, String bio);

    TeamProfileResponse updateTeamProfileImage(String userEmail, String profileImage);

    TeamProfileResponse getUserTeamProfile(Long userId);

    boolean hasTeamProfile(String userEmail);
}
