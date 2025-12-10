package kr.hyfata.rest.api.service.agora.impl;

import kr.hyfata.rest.api.dto.agora.team.TeamResponse;
import kr.hyfata.rest.api.dto.agora.team.CreateTeamRequest;
import kr.hyfata.rest.api.dto.agora.team.TeamMemberResponse;
import kr.hyfata.rest.api.dto.agora.team.TeamInvitationResponse;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.agora.*;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.*;
import kr.hyfata.rest.api.service.agora.AgoraTeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraTeamServiceImpl implements AgoraTeamService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRoleRepository teamRoleRepository;
    private final AgoraUserProfileRepository agoraUserProfileRepository;
    private final TeamProfileRepository teamProfileRepository;
    private final TeamInvitationRepository teamInvitationRepository;

    @Override
    public List<TeamResponse> getTeamList(String userEmail) {
        User user = findUserByEmail(userEmail);
        List<Team> teams = teamRepository.findTeamsByUserId(user.getId());
        return teams.stream()
                .map(TeamResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TeamResponse createTeam(String userEmail, CreateTeamRequest request) {
        User creator = findUserByEmail(userEmail);

        Team team = Team.builder()
                .name(request.getName())
                .description(request.getDescription())
                .profileImage(request.getProfileImage())
                .createdBy(creator)
                .isMain(false)
                .build();

        Team savedTeam = teamRepository.save(team);

        // Create default ADMIN role for creator
        TeamRole adminRole = TeamRole.builder()
                .team(savedTeam)
                .name("admin")
                .permissions("all")
                .build();
        TeamRole savedRole = teamRoleRepository.save(adminRole);

        // Add creator as member with ADMIN role
        TeamMember member = TeamMember.builder()
                .team(savedTeam)
                .user(creator)
                .role(savedRole)
                .build();
        teamMemberRepository.save(member);

        return TeamResponse.from(savedTeam);
    }

    @Override
    public TeamResponse getTeamDetail(String userEmail, Long teamId) {
        User user = findUserByEmail(userEmail);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        // Verify user is member
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, user.getId())) {
            throw new IllegalStateException("You are not a member of this team");
        }

        return TeamResponse.from(team);
    }

    @Override
    @Transactional
    public TeamResponse updateTeam(String userEmail, Long teamId, String name, String description, String profileImage) {
        User user = findUserByEmail(userEmail);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        // Verify user is creator
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("Only team creator can update team");
        }

        if (name != null && !name.isEmpty()) {
            team.setName(name);
        }
        if (description != null) {
            team.setDescription(description);
        }
        if (profileImage != null) {
            team.setProfileImage(profileImage);
        }

        Team updated = teamRepository.save(team);
        return TeamResponse.from(updated);
    }

    @Override
    @Transactional
    public String deleteTeam(String userEmail, Long teamId) {
        User user = findUserByEmail(userEmail);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        // Verify user is creator
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("Only team creator can delete team");
        }

        teamRepository.deleteById(teamId);
        return "Team deleted";
    }

    @Override
    public List<TeamMemberResponse> getTeamMembers(String userEmail, Long teamId) {
        User user = findUserByEmail(userEmail);

        // Verify user is member
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, user.getId())) {
            throw new IllegalStateException("You are not a member of this team");
        }

        List<TeamMember> members = teamMemberRepository.findByTeam_IdOrderByJoinedAtAsc(teamId);
        return members.stream()
                .map(member -> {
                    AgoraUserProfile agoraProfile = agoraUserProfileRepository.findById(member.getUser().getId()).orElse(null);
                    TeamProfile teamProfile = teamProfileRepository.findByUser_Id(member.getUser().getId()).orElse(null);
                    return TeamMemberResponse.from(member, agoraProfile, teamProfile);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TeamInvitationResponse sendInvitation(String userEmail, Long teamId, String targetAgoraId) {
        User inviter = findUserByEmail(userEmail);

        // Find target user by agoraId
        AgoraUserProfile targetAgoraProfile = agoraUserProfileRepository.findByAgoraId(targetAgoraId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with agoraId: " + targetAgoraId));
        User targetUser = targetAgoraProfile.getUser();

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        // Verify inviter is creator
        if (!team.getCreatedBy().getId().equals(inviter.getId())) {
            throw new IllegalStateException("Only team creator can invite members");
        }

        // Check if already member
        if (teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, targetUser.getId())) {
            throw new IllegalStateException("User is already a member of this team");
        }

        // Check if invitation already sent
        if (teamInvitationRepository.existsByTeam_IdAndToUser_IdAndStatus(teamId, targetUser.getId(), TeamInvitation.Status.PENDING)) {
            throw new IllegalStateException("Invitation already sent");
        }

        // Create team invitation
        TeamInvitation invitation = TeamInvitation.builder()
                .team(team)
                .fromUser(inviter)
                .toUser(targetUser)
                .status(TeamInvitation.Status.PENDING)
                .build();

        TeamInvitation saved = teamInvitationRepository.save(invitation);

        // Get profiles for response
        AgoraUserProfile fromAgoraProfile = agoraUserProfileRepository.findById(inviter.getId()).orElse(null);
        TeamProfile fromTeamProfile = teamProfileRepository.findByUser_Id(inviter.getId()).orElse(null);
        TeamProfile toTeamProfile = teamProfileRepository.findByUser_Id(targetUser.getId()).orElse(null);

        return TeamInvitationResponse.from(saved, fromAgoraProfile, fromTeamProfile, targetAgoraProfile, toTeamProfile);
    }

    @Override
    @Transactional
    public TeamInvitationResponse acceptInvitation(String userEmail, Long invitationId) {
        User user = findUserByEmail(userEmail);

        TeamInvitation invitation = teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        // Verify user is the invited one
        if (!invitation.getToUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the invited user can accept/reject");
        }

        // Check if already accepted or rejected
        if (invitation.getStatus() != TeamInvitation.Status.PENDING) {
            throw new IllegalStateException("Invitation already " + invitation.getStatus().toString().toLowerCase());
        }

        // Check TeamProfile exists
        TeamProfile teamProfile = teamProfileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException("TeamProfile is required to join a team"));

        // Create default MEMBER role if not exists
        Team team = invitation.getTeam();
        TeamRole memberRole = teamRoleRepository.findByTeam_IdAndName(team.getId(), "member")
                .orElseGet(() -> {
                    TeamRole role = TeamRole.builder()
                            .team(team)
                            .name("member")
                            .permissions("read,chat")
                            .build();
                    return teamRoleRepository.save(role);
                });

        // Add user as team member
        TeamMember newMember = TeamMember.builder()
                .team(team)
                .user(user)
                .role(memberRole)
                .build();
        teamMemberRepository.save(newMember);

        // Update invitation status
        invitation.setStatus(TeamInvitation.Status.ACCEPTED);
        TeamInvitation updated = teamInvitationRepository.save(invitation);

        // Get profiles for response
        AgoraUserProfile fromAgoraProfile = agoraUserProfileRepository.findById(invitation.getFromUser().getId()).orElse(null);
        TeamProfile fromTeamProfile = teamProfileRepository.findByUser_Id(invitation.getFromUser().getId()).orElse(null);
        AgoraUserProfile toAgoraProfile = agoraUserProfileRepository.findById(user.getId()).orElse(null);

        return TeamInvitationResponse.from(updated, fromAgoraProfile, fromTeamProfile, toAgoraProfile, teamProfile);
    }

    @Override
    @Transactional
    public TeamInvitationResponse rejectInvitation(String userEmail, Long invitationId) {
        User user = findUserByEmail(userEmail);

        TeamInvitation invitation = teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        // Verify user is the invited one
        if (!invitation.getToUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the invited user can accept/reject");
        }

        // Check if already accepted or rejected
        if (invitation.getStatus() != TeamInvitation.Status.PENDING) {
            throw new IllegalStateException("Invitation already " + invitation.getStatus().toString().toLowerCase());
        }

        // Update invitation status
        invitation.setStatus(TeamInvitation.Status.REJECTED);
        TeamInvitation updated = teamInvitationRepository.save(invitation);

        // Get profiles for response
        AgoraUserProfile fromAgoraProfile = agoraUserProfileRepository.findById(invitation.getFromUser().getId()).orElse(null);
        TeamProfile fromTeamProfile = teamProfileRepository.findByUser_Id(invitation.getFromUser().getId()).orElse(null);
        AgoraUserProfile toAgoraProfile = agoraUserProfileRepository.findById(user.getId()).orElse(null);
        TeamProfile toTeamProfile = teamProfileRepository.findByUser_Id(user.getId()).orElse(null);

        return TeamInvitationResponse.from(updated, fromAgoraProfile, fromTeamProfile, toAgoraProfile, toTeamProfile);
    }

    @Override
    public List<TeamInvitationResponse> getReceivedInvitations(String userEmail) {
        User user = findUserByEmail(userEmail);

        List<TeamInvitation> invitations = teamInvitationRepository.findByToUser_IdOrderByCreatedAtDesc(user.getId());
        return invitations.stream()
                .map(invitation -> {
                    AgoraUserProfile fromAgoraProfile = agoraUserProfileRepository.findById(invitation.getFromUser().getId()).orElse(null);
                    TeamProfile fromTeamProfile = teamProfileRepository.findByUser_Id(invitation.getFromUser().getId()).orElse(null);
                    AgoraUserProfile toAgoraProfile = agoraUserProfileRepository.findById(user.getId()).orElse(null);
                    TeamProfile toTeamProfile = teamProfileRepository.findByUser_Id(user.getId()).orElse(null);
                    return TeamInvitationResponse.from(invitation, fromAgoraProfile, fromTeamProfile, toAgoraProfile, toTeamProfile);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<TeamInvitationResponse> getSentInvitations(String userEmail, Long teamId) {
        User user = findUserByEmail(userEmail);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        // Verify user is creator
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("Only team creator can view sent invitations");
        }

        List<TeamInvitation> invitations = teamInvitationRepository.findByTeam_IdOrderByCreatedAtDesc(teamId);
        return invitations.stream()
                .map(invitation -> {
                    AgoraUserProfile fromAgoraProfile = agoraUserProfileRepository.findById(user.getId()).orElse(null);
                    TeamProfile fromTeamProfile = teamProfileRepository.findByUser_Id(user.getId()).orElse(null);
                    AgoraUserProfile toAgoraProfile = agoraUserProfileRepository.findById(invitation.getToUser().getId()).orElse(null);
                    TeamProfile toTeamProfile = teamProfileRepository.findByUser_Id(invitation.getToUser().getId()).orElse(null);
                    return TeamInvitationResponse.from(invitation, fromAgoraProfile, fromTeamProfile, toAgoraProfile, toTeamProfile);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public String removeMember(String userEmail, Long teamId, Long memberId) {
        User user = findUserByEmail(userEmail);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        // Verify user is creator
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("Only team creator can remove members");
        }

        TeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found"));

        // Cannot remove creator
        if (member.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Creator cannot be removed");
        }

        teamMemberRepository.deleteById(memberId);
        return "Member removed";
    }

    @Override
    @Transactional
    public String changeMemberRole(String userEmail, Long teamId, Long memberId, String roleName) {
        User user = findUserByEmail(userEmail);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        // Verify user is creator
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("Only team creator can change member roles");
        }

        TeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found"));

        TeamRole role = teamRoleRepository.findByTeam_IdAndName(teamId, roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        member.setRole(role);
        teamMemberRepository.save(member);

        return "Member role changed";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
