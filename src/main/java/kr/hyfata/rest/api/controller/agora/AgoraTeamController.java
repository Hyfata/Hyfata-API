package kr.hyfata.rest.api.controller.agora;

import kr.hyfata.rest.api.dto.agora.chat.ChatResponse;
import kr.hyfata.rest.api.dto.agora.team.TeamResponse;
import kr.hyfata.rest.api.dto.agora.team.CreateTeamRequest;
import kr.hyfata.rest.api.dto.agora.team.TeamMemberResponse;
import kr.hyfata.rest.api.dto.agora.team.SendTeamInvitationRequest;
import kr.hyfata.rest.api.dto.agora.team.TeamInvitationResponse;
import kr.hyfata.rest.api.service.agora.AgoraChatService;
import kr.hyfata.rest.api.service.agora.AgoraTeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agora/teams")
@RequiredArgsConstructor
@Slf4j
public class AgoraTeamController {

    private final AgoraTeamService agoraTeamService;
    private final AgoraChatService agoraChatService;

    /**
     * 팀 목록 조회
     * GET /api/agora/teams
     */
    @GetMapping
    public ResponseEntity<List<TeamResponse>> getTeamList(Authentication authentication) {
        String userEmail = authentication.getName();
        List<TeamResponse> teams = agoraTeamService.getTeamList(userEmail);
        return ResponseEntity.ok(teams);
    }

    /**
     * 팀 생성
     * POST /api/agora/teams
     */
    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(
            Authentication authentication,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        String userEmail = authentication.getName();
        TeamResponse team = agoraTeamService.createTeam(userEmail, request);
        return ResponseEntity.ok(team);
    }

    /**
     * 팀 상세 조회
     * GET /api/agora/teams/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeamDetail(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String userEmail = authentication.getName();
        TeamResponse team = agoraTeamService.getTeamDetail(userEmail, id);
        return ResponseEntity.ok(team);
    }

    /**
     * 팀 수정
     * PUT /api/agora/teams/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String profileImage
    ) {
        String userEmail = authentication.getName();
        TeamResponse team = agoraTeamService.updateTeam(userEmail, id, name, description, profileImage);
        return ResponseEntity.ok(team);
    }

    /**
     * 팀 삭제
     * DELETE /api/agora/teams/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTeam(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String userEmail = authentication.getName();
        String message = agoraTeamService.deleteTeam(userEmail, id);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * 팀원 목록 조회
     * GET /api/agora/teams/{id}/members
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String userEmail = authentication.getName();
        List<TeamMemberResponse> members = agoraTeamService.getTeamMembers(userEmail, id);
        return ResponseEntity.ok(members);
    }

    /**
     * 팀원 초대 (agoraId 사용)
     * POST /api/agora/teams/{id}/invitations
     */
    @PostMapping("/{id}/invitations")
    public ResponseEntity<TeamInvitationResponse> sendInvitation(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody SendTeamInvitationRequest request
    ) {
        String userEmail = authentication.getName();
        TeamInvitationResponse invitation = agoraTeamService.sendInvitation(userEmail, id, request.getAgoraId());
        return ResponseEntity.ok(invitation);
    }

    /**
     * 팀 초대 수락
     * POST /api/agora/teams/invitations/{invitationId}/accept
     */
    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<TeamInvitationResponse> acceptInvitation(
            Authentication authentication,
            @PathVariable Long invitationId
    ) {
        String userEmail = authentication.getName();
        TeamInvitationResponse invitation = agoraTeamService.acceptInvitation(userEmail, invitationId);
        return ResponseEntity.ok(invitation);
    }

    /**
     * 팀 초대 거절
     * POST /api/agora/teams/invitations/{invitationId}/reject
     */
    @PostMapping("/invitations/{invitationId}/reject")
    public ResponseEntity<TeamInvitationResponse> rejectInvitation(
            Authentication authentication,
            @PathVariable Long invitationId
    ) {
        String userEmail = authentication.getName();
        TeamInvitationResponse invitation = agoraTeamService.rejectInvitation(userEmail, invitationId);
        return ResponseEntity.ok(invitation);
    }

    /**
     * 받은 팀 초대 목록 조회
     * GET /api/agora/teams/invitations/received
     */
    @GetMapping("/invitations/received")
    public ResponseEntity<List<TeamInvitationResponse>> getReceivedInvitations(
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        List<TeamInvitationResponse> invitations = agoraTeamService.getReceivedInvitations(userEmail);
        return ResponseEntity.ok(invitations);
    }

    /**
     * 보낸 팀 초대 목록 조회
     * GET /api/agora/teams/{id}/invitations
     */
    @GetMapping("/{id}/invitations")
    public ResponseEntity<List<TeamInvitationResponse>> getSentInvitations(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String userEmail = authentication.getName();
        List<TeamInvitationResponse> invitations = agoraTeamService.getSentInvitations(userEmail, id);
        return ResponseEntity.ok(invitations);
    }

    /**
     * 팀원 제거
     * DELETE /api/agora/teams/{id}/members/{memberId}
     */
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<?> removeMember(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long memberId
    ) {
        String userEmail = authentication.getName();
        String message = agoraTeamService.removeMember(userEmail, id, memberId);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * 팀원 역할 변경
     * PUT /api/agora/teams/{id}/members/{memberId}/role
     */
    @PutMapping("/{id}/members/{memberId}/role")
    public ResponseEntity<?> changeMemberRole(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long memberId,
            @RequestParam String roleName
    ) {
        String userEmail = authentication.getName();
        String message = agoraTeamService.changeMemberRole(userEmail, id, memberId, roleName);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * 팀 그룹 채팅 조회
     * GET /api/agora/teams/{id}/chat
     */
    @GetMapping("/{id}/chat")
    public ResponseEntity<ChatResponse> getTeamGroupChat(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String userEmail = authentication.getName();
        ChatResponse chat = agoraChatService.getTeamGroupChat(id, userEmail);
        return ResponseEntity.ok(chat);
    }
}
