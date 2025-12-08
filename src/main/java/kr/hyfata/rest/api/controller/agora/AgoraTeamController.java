package kr.hyfata.rest.api.controller.agora;

import kr.hyfata.rest.api.dto.agora.chat.ChatResponse;
import kr.hyfata.rest.api.dto.agora.team.TeamResponse;
import kr.hyfata.rest.api.dto.agora.team.CreateTeamRequest;
import kr.hyfata.rest.api.dto.agora.team.TeamMemberResponse;
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
     * 팀원 초대
     * POST /api/agora/teams/{id}/members
     */
    @PostMapping("/{id}/members")
    public ResponseEntity<TeamMemberResponse> inviteMember(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String userEmail
    ) {
        String requesterEmail = authentication.getName();
        TeamMemberResponse member = agoraTeamService.inviteMember(requesterEmail, id, userEmail);
        return ResponseEntity.ok(member);
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
