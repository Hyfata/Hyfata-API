package kr.hyfata.rest.api.oauth;

import kr.hyfata.rest.api.session.AuthorizationRevoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Component;

/**
 * {@link AuthorizationRevoker}의 SAS 어댑터.
 * SAS 로그인 토큰 폐기
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SasAuthorizationRevoker implements AuthorizationRevoker {

    private final ObjectProvider<OAuth2AuthorizationService> authorizationServiceProvider;

    @Override
    public void revoke(String authorizationId) {
        if (authorizationId == null) {
            return;  // SAS authorization이 없는 세션
        }
        try {
            OAuth2AuthorizationService authorizationService = authorizationServiceProvider.getIfAvailable();
            if (authorizationService == null) {
                return;
            }
            OAuth2Authorization authorization = authorizationService.findById(authorizationId);
            if (authorization != null) {
                authorizationService.remove(authorization);
            }
        } catch (Exception e) {
            log.error("Failed to remove SAS authorization (id={}): {}", authorizationId, e.getMessage());
        }
    }
}
