package kr.hyfata.rest.api.controller.agora;

import jakarta.validation.Valid;
import kr.hyfata.rest.api.dto.agora.AgoraProfileResponse;
import kr.hyfata.rest.api.dto.agora.CreateAgoraProfileRequest;
import kr.hyfata.rest.api.dto.agora.UpdateAgoraProfileRequest;
import kr.hyfata.rest.api.service.agora.AgoraProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agora/profile")
@RequiredArgsConstructor
@Slf4j
public class AgoraProfileController {

    private final AgoraProfileService agoraProfileService;

    /**
     * 내 Agora 프로필 조회
     * GET /api/agora/profile
     */
    @GetMapping
    public ResponseEntity<?> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        AgoraProfileResponse profile = agoraProfileService.getMyProfile(email);

        if (profile == null) {
            return ResponseEntity.ok(Map.of(
                    "message", "Agora profile not found. Please create a profile first.",
                    "hasProfile", false
            ));
        }

        return ResponseEntity.ok(profile);
    }

    /**
     * Agora 프로필 생성
     * POST /api/agora/profile
     */
    @PostMapping
    public ResponseEntity<AgoraProfileResponse> createProfile(
            Authentication authentication,
            @Valid @RequestBody CreateAgoraProfileRequest request
    ) {
        String email = authentication.getName();
        AgoraProfileResponse profile = agoraProfileService.createProfile(email, request);
        return ResponseEntity.ok(profile);
    }

    /**
     * Agora 프로필 수정
     * PUT /api/agora/profile
     */
    @PutMapping
    public ResponseEntity<AgoraProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateAgoraProfileRequest request
    ) {
        String email = authentication.getName();
        AgoraProfileResponse profile = agoraProfileService.updateProfile(email, request);
        return ResponseEntity.ok(profile);
    }
}
