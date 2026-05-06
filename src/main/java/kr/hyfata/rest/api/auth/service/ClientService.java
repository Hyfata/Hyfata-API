package kr.hyfata.rest.api.auth.service;

import kr.hyfata.rest.api.auth.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.auth.dto.ClientResponse;
import kr.hyfata.rest.api.auth.entity.Client;
import org.springframework.security.core.Authentication;

import java.util.Optional;

public interface ClientService {
    ClientResponse registerClient(ClientRegistrationRequest request, Authentication authentication);
    Optional<ClientResponse> getClient(String clientId);
    Optional<Client> validateClient(String clientId);
    boolean existsClient(String clientId);
}
