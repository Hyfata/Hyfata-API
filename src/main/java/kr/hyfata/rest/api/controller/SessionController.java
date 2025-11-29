package kr.hyfata.rest.api.controller;

import kr.hyfata.rest.api.dto.SessionListResponse;
import kr.hyfata.rest.api.dto.UserSessionDTO;
import kr.hyfata.rest.api.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 세션 관리 컨트롤러
 * 활성 세션 조회, 원격 로그아웃 등
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final SessionService sessionService;

    /**
     * 활성 세션 목록 조회
     * GET /api/sessions
     */
    @GetMapping
    public ResponseEntity<SessionListResponse> getActiveSessions(
            Authentication authentication,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken
    ) {
        String email = authentication.getName();
        List<UserSessionDTO> sessions = sessionService.getActiveSessions(email, refreshToken);

        return ResponseEntity.ok(SessionListResponse.of(sessions));
    }

    /**
     * 특정 세션 무효화 (원격 로그아웃)
     * DELETE /api/sessions/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> revokeSession(
            Authentication authentication,
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String email = authentication.getName();
        String currentAccessToken = extractToken(authHeader);

        sessionService.revokeSession(email, sessionId, currentAccessToken);

        return ResponseEntity.ok(Map.of(
                "message", "Session revoked successfully"
        ));
    }

    /**
     * 다른 모든 세션 무효화 (현재 세션 제외)
     * POST /api/sessions/revoke-others
     */
    @PostMapping("/revoke-others")
    public ResponseEntity<Map<String, String>> revokeOtherSessions(
            Authentication authentication,
            @RequestHeader(value = "X-Refresh-Token") String refreshToken
    ) {
        String email = authentication.getName();
        sessionService.revokeOtherSessions(email, refreshToken);

        return ResponseEntity.ok(Map.of(
                "message", "Other sessions revoked successfully"
        ));
    }

    /**
     * 모든 세션 무효화 (전체 로그아웃)
     * POST /api/sessions/revoke-all
     */
    @PostMapping("/revoke-all")
    public ResponseEntity<Map<String, String>> revokeAllSessions(
            Authentication authentication
    ) {
        String email = authentication.getName();
        sessionService.revokeAllSessions(email);

        return ResponseEntity.ok(Map.of(
                "message", "All sessions revoked successfully"
        ));
    }

    /**
     * Authorization 헤더에서 토큰 추출
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
