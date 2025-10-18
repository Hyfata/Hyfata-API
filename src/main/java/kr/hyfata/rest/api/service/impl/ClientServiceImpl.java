package kr.hyfata.rest.api.service.impl;

import kr.hyfata.rest.api.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.dto.ClientResponse;
import kr.hyfata.rest.api.entity.Client;
import kr.hyfata.rest.api.repository.ClientRepository;
import kr.hyfata.rest.api.service.ClientService;
import kr.hyfata.rest.api.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final TokenGenerator tokenGenerator;

    @Override
    public ClientResponse registerClient(ClientRegistrationRequest request) {
        // clientId와 clientSecret 생성
        String clientId = generateUniqueClientId();
        String clientSecret = tokenGenerator.generatePasswordResetToken();  // 긴 난수 토큰 사용

        // redirectUris를 쉼표로 구분된 문자열로 변환
        String redirectUrisStr = String.join(",", request.getRedirectUris());

        Client client = Client.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .name(request.getName())
                .description(request.getDescription())
                .frontendUrl(request.getFrontendUrl())
                .redirectUris(redirectUrisStr)
                .enabled(true)
                .maxTokensPerUser(request.getMaxTokensPerUser() != null ? request.getMaxTokensPerUser() : 5)
                .build();

        Client savedClient = clientRepository.save(client);
        log.info("Client registered: {} ({})", request.getName(), clientId);

        return mapToResponse(savedClient);
    }

    @Override
    public Optional<ClientResponse> getClient(String clientId) {
        return clientRepository.findByClientId(clientId)
                .map(this::mapToResponse);
    }

    @Override
    public Optional<Client> validateClient(String clientId) {
        Optional<Client> client = clientRepository.findByClientId(clientId);

        if (client.isEmpty()) {
            log.warn("Client not found: {}", clientId);
            return Optional.empty();
        }

        if (!client.get().getEnabled()) {
            log.warn("Client is disabled: {}", clientId);
            return Optional.empty();
        }

        return client;
    }

    @Override
    public boolean existsClient(String clientId) {
        return clientRepository.existsByClientId(clientId);
    }

    /**
     * 고유한 clientId 생성
     */
    private String generateUniqueClientId() {
        String clientId;
        do {
            clientId = "client_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        } while (clientRepository.existsByClientId(clientId));

        return clientId;
    }

    /**
     * Client 엔티티를 ClientResponse DTO로 변환
     */
    private ClientResponse mapToResponse(Client client) {
        List<String> redirectUris = List.of(client.getRedirectUris().split(","));

        return ClientResponse.builder()
                .id(client.getId())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .name(client.getName())
                .description(client.getDescription())
                .frontendUrl(client.getFrontendUrl())
                .redirectUris(redirectUris)
                .enabled(client.getEnabled())
                .maxTokensPerUser(client.getMaxTokensPerUser())
                .ownerEmail(client.getOwnerEmail())
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }
}
