package kr.hyfata.rest.api.auth.service;

import kr.hyfata.rest.api.auth.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.auth.dto.ClientResponse;
import org.springframework.security.core.Authentication;

import java.util.Optional;

public interface ClientService {
    ClientResponse registerClient(ClientRegistrationRequest request, Authentication authentication);
    Optional<ClientResponse> getClient(String clientId);
    /**
     * 클라이언트가 존재하는지 검증 (회원가입/비밀번호 재설정 등에서 사용)
     */
    boolean validateClient(String clientId);
    boolean existsClient(String clientId);
}
