package kr.hyfata.rest.api.service;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.dto.UserSessionDTO;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.UserSession;

import java.util.List;

/**
 * 세션 관리 서비스 인터페이스
 */
public interface SessionService {

    /**
     * 새 세션 생성 (로그인 시)
     * @param user 사용자
     * @param refreshToken Refresh Token (원본)
     * @param accessTokenJti Access Token의 JTI
     * @param request HTTP 요청 (IP, User-Agent 추출용)
     * @return 생성된 세션
     */
    UserSession createSession(User user, String refreshToken, String accessTokenJti, HttpServletRequest request);

    /**
     * 새 세션 생성 (PKCE 플로우 지정)
     * @param user 사용자
     * @param refreshToken Refresh Token (원본)
     * @param accessTokenJti Access Token의 JTI
     * @param request HTTP 요청 (IP, User-Agent 추출용)
     * @param isPkceFlow PKCE 기반 Public Client 여부
     * @return 생성된 세션
     */
    UserSession createSession(User user, String refreshToken, String accessTokenJti, HttpServletRequest request, boolean isPkceFlow);

    /**
     * 사용자의 활성 세션 목록 조회
     * @param userEmail 사용자 이메일
     * @param currentRefreshToken 현재 세션의 Refresh Token (현재 세션 표시용)
     * @return 세션 DTO 목록
     */
    List<UserSessionDTO> getActiveSessions(String userEmail, String currentRefreshToken);

    /**
     * 특정 세션 무효화 (원격 로그아웃)
     * @param userEmail 사용자 이메일
     * @param sessionId 세션 ID (refreshTokenHash)
     * @param currentAccessToken 현재 Access Token (블랙리스트 등록용)
     */
    void revokeSession(String userEmail, String sessionId, String currentAccessToken);

    /**
     * 모든 세션 무효화 (전체 로그아웃)
     * @param userEmail 사용자 이메일
     */
    void revokeAllSessions(String userEmail);

    /**
     * 현재 세션 제외 모든 세션 무효화
     * @param userEmail 사용자 이메일
     * @param currentRefreshToken 현재 세션의 Refresh Token
     */
    void revokeOtherSessions(String userEmail, String currentRefreshToken);

    /**
     * Refresh Token으로 세션 유효성 검증
     * @param refreshToken Refresh Token (원본)
     * @return 유효하면 true
     */
    boolean validateSession(String refreshToken);

    /**
     * 세션 활동 시간 갱신 + Access Token JTI 업데이트
     * @param refreshToken Refresh Token (원본)
     * @param newAccessTokenJti 새 Access Token의 JTI
     */
    void updateSessionActivity(String refreshToken, String newAccessTokenJti);

    /**
     * Refresh Token 해시 생성
     */
    String hashToken(String token);
}
