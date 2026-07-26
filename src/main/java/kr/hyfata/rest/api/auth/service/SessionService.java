package kr.hyfata.rest.api.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import kr.hyfata.rest.api.auth.dto.UserSessionDTO;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.entity.UserSession;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 세션 관리 서비스 인터페이스
 */
public interface SessionService {

    /**
     * SAS 토큰 발급/로테이션에 연동된 세션 생성 (SessionBridgingAuthorizationService 전용)
     * <p>
     * SAS는 모든 클라이언트에 PKCE를 강제하므로 pkceFlow는 항상 true로 기록된다.
     * request가 null인 경우(요청 컨텍스트 없음) 디바이스 정보 없이 안전하게 생성된다.
     *
     * @param user 사용자
     * @param refreshToken SAS Refresh Token (원본, opaque)
     * @param accessTokenJti SAS Access Token의 JTI
     * @param clientId SAS RegisteredClient의 client_id
     * @param authorizationId SAS OAuth2Authorization ID
     * @param scopes 발급된 scope 목록
     * @param request HTTP 요청 (nullable)
     * @return 생성된 세션
     */
    UserSession createSasSession(User user, String refreshToken, String accessTokenJti,
                                 String clientId, String authorizationId, Set<String> scopes,
                                 HttpServletRequest request);

    /**
     * SAS OAuth2Authorization ID로 세션 조회 (세션 브리징용)
     * @param authorizationId SAS OAuth2Authorization ID
     * @return 세션 (없으면 empty)
     */
    Optional<UserSession> findByAuthorizationId(String authorizationId);

    /**
     * 엔티티 기반 세션 무효화 (SAS 브리징 전용)
     * <p>
     * 본인 확인 없이 세션 무효화 + Access Token JTI 블랙리스트 등록만 수행한다.
     * SAS authorizationService.remove()는 호출하지 않으므로 데코레이터와의 순환 호출이 없다.
     *
     * @param session 무효화할 세션
     */
    void revokeSessionEntity(UserSession session);

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
     * Refresh Token 해시 생성
     */
    String hashToken(String token);

    /**
     * OAuth 서버사이드 세션(Redis)을 포함한 모든 세션 무효화
     * @param userEmail 사용자 이메일
     */
    void revokeAllOAuthSessions(String userEmail);
}
