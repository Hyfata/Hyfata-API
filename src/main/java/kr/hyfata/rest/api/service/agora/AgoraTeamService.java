package kr.hyfata.rest.api.service.agora;

import kr.hyfata.rest.api.dto.agora.team.TeamResponse;
import kr.hyfata.rest.api.dto.agora.team.CreateTeamRequest;
import kr.hyfata.rest.api.dto.agora.team.TeamMemberResponse;
import kr.hyfata.rest.api.dto.agora.team.TeamInvitationResponse;

import java.util.List;

public interface AgoraTeamService {

    List<TeamResponse> getTeamList(String userEmail);

    TeamResponse createTeam(String userEmail, CreateTeamRequest request);

    TeamResponse getTeamDetail(String userEmail, Long teamId);

    TeamResponse updateTeam(String userEmail, Long teamId, String name, String description, String profileImage);

    String deleteTeam(String userEmail, Long teamId);

    List<TeamMemberResponse> getTeamMembers(String userEmail, Long teamId);

    String removeMember(String userEmail, Long teamId, Long memberId);

    String changeMemberRole(String userEmail, Long teamId, Long memberId, String roleName);

    // Team Invitation methods
    TeamInvitationResponse sendInvitation(String userEmail, Long teamId, String targetAgoraId);

    TeamInvitationResponse acceptInvitation(String userEmail, Long invitationId);

    TeamInvitationResponse rejectInvitation(String userEmail, Long invitationId);

    List<TeamInvitationResponse> getReceivedInvitations(String userEmail);

    List<TeamInvitationResponse> getSentInvitations(String userEmail, Long teamId);
}
