package kr.hyfata.rest.api.agora.team.service;

import kr.hyfata.rest.api.agora.team.dto.TeamProfileResponse;
import kr.hyfata.rest.api.agora.team.dto.CreateTeamProfileRequest;

public interface AgoraTeamProfileService {

    TeamProfileResponse getMyTeamProfile(String userEmail);

    TeamProfileResponse createTeamProfile(String userEmail, CreateTeamProfileRequest request);

    TeamProfileResponse updateTeamProfile(String userEmail, String displayName, String profileImage, String bio);

    TeamProfileResponse updateTeamProfileImage(String userEmail, String profileImage);

    TeamProfileResponse getUserTeamProfile(Long userId);

    boolean hasTeamProfile(String userEmail);
}
