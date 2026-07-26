package kr.hyfata.rest.api.auth.service.impl;

import kr.hyfata.rest.api.auth.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.auth.dto.ClientResponse;
import kr.hyfata.rest.api.auth.entity.ClientMetadata;
import kr.hyfata.rest.api.auth.entity.ClientType;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.repository.ClientMetadataRepository;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.ClientService;
import kr.hyfata.rest.api.common.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * OAuth 클라이언트 관리 서비스
 * <p>
 * 프로토콜 정보는 SAS 표준 oauth2_registered_client 테이블(RegisteredClientRepository)에,
 * 정보성 메타데이터(frontendUrl, description, owner, clientType)는 client_metadata 테이블에 저장한다.
 * 이 API로 등록되는 클라이언트는 모두 THIRD_PARTY이며 confidential(client_secret 발급)이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientServiceImpl implements ClientService {

    private final RegisteredClientRepository registeredClientRepository;
    private final ClientMetadataRepository clientMetadataRepository;
    private final UserRepository userRepository;
    private final TokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;

    @Override
    public ClientResponse registerClient(ClientRegistrationRequest request, Authentication authentication) {
        // 관리자 여부 확인
        boolean isAdmin = authentication != null && authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

        // clientId와 clientSecret 생성
        String clientId = generateUniqueClientId();
        String clientSecret = tokenGenerator.generatePasswordResetToken();  // 긴 난수 토큰 사용
        String hashedClientSecret = passwordEncoder.encode(clientSecret);  // BCrypt로 해싱

        // scope 설정: 비관리자는 profile email로 강제 제한
        String allowedScopes;
        if (isAdmin) {
            allowedScopes = StringUtils.hasText(request.getAllowedScopes())
                    ? request.getAllowedScopes()
                    : "profile email";
        } else {
            allowedScopes = "profile email";
            if (StringUtils.hasText(request.getAllowedScopes())) {
                log.warn("Non-admin user attempted to set custom scopes. Forced to 'profile email'. user={}",
                        authentication != null ? authentication.getName() : "anonymous");
            }
        }

        // SAS RegisteredClient 저장 (third-party이므로 consent 필요)
        Set<String> scopes = Set.of(allowedScopes.split(" "));
        RegisteredClient registeredClient = RegisteredClientFactory.build(
                clientId, hashedClientSecret, request.getName(),
                Set.copyOf(request.getRedirectUris()), scopes, true);
        registeredClientRepository.save(registeredClient);

        // 메타데이터 저장
        ClientMetadata metadata = ClientMetadata.builder()
                .clientId(clientId)
                .description(request.getDescription())
                .frontendUrl(request.getFrontendUrl())
                .clientType(ClientType.THIRD_PARTY)  // API로 생성되는 클라이언트는 모두 Third-Party
                .build();

        // 소유자 설정 (optional)
        if (request.getOwnerId() != null) {
            User owner = userRepository.findById(request.getOwnerId())
                    .orElseThrow(() -> new IllegalArgumentException("Owner user not found with id: " + request.getOwnerId()));
            metadata.setOwner(owner);
        }

        ClientMetadata savedMetadata = clientMetadataRepository.save(metadata);
        log.info("Client registered: {} ({}) by user={} (admin={})", request.getName(), clientId,
                authentication != null ? authentication.getName() : "anonymous", isAdmin);

        // 생성 시에만 평문 clientSecret을 응답에 포함
        return toResponse(registeredClient, savedMetadata, clientSecret);
    }

    @Override
    public Optional<ClientResponse> getClient(String clientId) {
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            log.warn("Client not found: {}", clientId);
            return Optional.empty();
        }

        ClientMetadata metadata = clientMetadataRepository.findById(clientId).orElse(null);
        return Optional.of(toResponse(registeredClient, metadata, null));
    }

    @Override
    public boolean validateClient(String clientId) {
        return existsClient(clientId);
    }

    @Override
    public boolean existsClient(String clientId) {
        return registeredClientRepository.findByClientId(clientId) != null;
    }

    /**
     * 고유한 clientId 생성
     */
    private String generateUniqueClientId() {
        String clientId;
        do {
            clientId = "client_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        } while (registeredClientRepository.findByClientId(clientId) != null);

        return clientId;
    }

    /**
     * RegisteredClient + 메타데이터를 ClientResponse DTO로 병합
     * @param plainClientSecret 생성 시에만 평문 시크릿 전달, 그 외 null
     */
    private ClientResponse toResponse(RegisteredClient registeredClient, ClientMetadata metadata,
                                      String plainClientSecret) {
        return ClientResponse.builder()
                .clientId(registeredClient.getClientId())
                .clientSecret(plainClientSecret)
                .name(registeredClient.getClientName())
                .description(metadata != null ? metadata.getDescription() : null)
                .frontendUrl(metadata != null ? metadata.getFrontendUrl() : null)
                .redirectUris(List.copyOf(registeredClient.getRedirectUris()))
                .allowedScopes(String.join(" ", registeredClient.getScopes()))
                .clientType(metadata != null ? metadata.getClientType() : ClientType.THIRD_PARTY)
                .ownerId(metadata != null && metadata.getOwner() != null ? metadata.getOwner().getId() : null)
                .createdAt(metadata != null ? metadata.getCreatedAt() : null)
                .updatedAt(metadata != null ? metadata.getUpdatedAt() : null)
                .build();
    }
}
