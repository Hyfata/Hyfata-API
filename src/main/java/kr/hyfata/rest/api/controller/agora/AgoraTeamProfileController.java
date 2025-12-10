package kr.hyfata.rest.api.controller.agora;

import kr.hyfata.rest.api.dto.agora.team.TeamProfileResponse;
import kr.hyfata.rest.api.dto.agora.team.CreateTeamProfileRequest;
import kr.hyfata.rest.api.service.agora.AgoraTeamProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/agora/team-profile")
@RequiredArgsConstructor
@Slf4j
public class AgoraTeamProfileController {

    private final AgoraTeamProfileService agoraTeamProfileService;

    /**
     * 내 팀 프로필 조회
     * GET /api/agora/team-profile
     */
    @GetMapping
    public ResponseEntity<TeamProfileResponse> getMyTeamProfile(Authentication authentication) {
        String userEmail = authentication.getName();
        TeamProfileResponse profile = agoraTeamProfileService.getMyTeamProfile(userEmail);
        return ResponseEntity.ok(profile);
    }

    /**
     * 팀 프로필 생성
     * POST /api/agora/team-profile
     */
    @PostMapping
    public ResponseEntity<TeamProfileResponse> createTeamProfile(
            Authentication authentication,
            @Valid @RequestBody CreateTeamProfileRequest request
    ) {
        String userEmail = authentication.getName();
        TeamProfileResponse profile = agoraTeamProfileService.createTeamProfile(userEmail, request);
        return ResponseEntity.ok(profile);
    }

    /**
     * 팀 프로필 수정
     * PUT /api/agora/team-profile
     */
    @PutMapping
    public ResponseEntity<TeamProfileResponse> updateTeamProfile(
            Authentication authentication,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String profileImage,
            @RequestParam(required = false) String bio
    ) {
        String userEmail = authentication.getName();
        TeamProfileResponse profile = agoraTeamProfileService.updateTeamProfile(userEmail, displayName, profileImage, bio);
        return ResponseEntity.ok(profile);
    }

    /**
     * 팀 프로필 이미지 변경
     * PUT /api/agora/team-profile/image
     */
    @PutMapping("/image")
    public ResponseEntity<TeamProfileResponse> updateTeamProfileImage(
            Authentication authentication,
            @RequestParam String profileImage
    ) {
        String userEmail = authentication.getName();
        TeamProfileResponse profile = agoraTeamProfileService.updateTeamProfileImage(userEmail, profileImage);
        return ResponseEntity.ok(profile);
    }

    /**
     * 특정 사용자의 팀 프로필 조회
     * GET /api/agora/team-profile/users/{userId}
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<TeamProfileResponse> getUserTeamProfile(@PathVariable Long userId) {
        TeamProfileResponse profile = agoraTeamProfileService.getUserTeamProfile(userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * 팀 프로필 존재 여부 확인
     * GET /api/agora/team-profile/exists
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> hasTeamProfile(Authentication authentication) {
        String userEmail = authentication.getName();
        boolean exists = agoraTeamProfileService.hasTeamProfile(userEmail);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}
