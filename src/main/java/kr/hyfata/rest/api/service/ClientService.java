package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.dto.ClientResponse;
import kr.hyfata.rest.api.entity.Client;

import java.util.Optional;

public interface ClientService {
    ClientResponse registerClient(ClientRegistrationRequest request);
    Optional<ClientResponse> getClient(String clientId);
    Optional<Client> validateClient(String clientId);
    boolean existsClient(String clientId);
}
