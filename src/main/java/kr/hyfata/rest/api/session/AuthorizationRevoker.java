package kr.hyfata.rest.api.session;

/**
 * Authorization 무효화 포트.
 * <p>
 * 세션 도메인이 토큰 저장소(SAS OAuth2AuthorizationService)를 직접 알지 못하도록
 * 분리하는 인터페이스. 구현체는 SAS 어댑터({@code SasAuthorizationRevoker})가 담당한다.
 */
public interface AuthorizationRevoker {

    /**
     * 주어진 authorization ID의 authorization을 제거해 refresh token까지 무효화한다.
     * authorization이 없거나 이미 제거된 경우 아무 동작도 하지 않는다.
     *
     * @param authorizationId SAS OAuth2Authorization ID (null이면 무시)
     */
    void revoke(String authorizationId);
}
