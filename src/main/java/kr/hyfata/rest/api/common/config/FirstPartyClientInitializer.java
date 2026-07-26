package kr.hyfata.rest.api.common.config;

import kr.hyfata.rest.api.auth.entity.ClientMetadata;
import kr.hyfata.rest.api.auth.entity.ClientType;
import kr.hyfata.rest.api.auth.repository.ClientMetadataRepository;
import kr.hyfata.rest.api.auth.service.impl.RegisteredClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * First-Party OAuth 클라이언트 초기화
 * <p>
 * 애플리케이션 시작 시 설정 파일에 정의된 공식 클라이언트를
 * SAS 표준 oauth2_registered_client 테이블(RegisteredClientRepository)과
 * client_metadata 테이블에 시드/동기화한다.
 * clientSecret이 없으면 public(NONE 인증) 클라이언트로 등록된다.
 * FIRST_PARTY는 consent 화면이 생략된다 (requireAuthorizationConsent=false).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirstPartyClientInitializer implements ApplicationRunner {

    private final FirstPartyClientProperties firstPartyClientProperties;
    private final RegisteredClientRepository registeredClientRepository;
    private final ClientMetadataRepository clientMetadataRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var clients = firstPartyClientProperties.getClients();

        if (clients == null || clients.isEmpty()) {
            log.debug("No first-party clients configured");
            return;
        }

        for (FirstPartyClientProperties.ClientConfig config : clients) {
            if (!StringUtils.hasText(config.getClientId())) {
                log.warn("Skipping first-party client with empty clientId");
                continue;
            }

            if (!Boolean.TRUE.equals(config.getEnabled())) {
                log.info("First-party client '{}' is disabled, skipping", config.getClientId());
                continue;
            }

            // clientSecret이 없으면 public 클라이언트(NONE 인증, PKCE 필수)로 등록
            String secretHash = StringUtils.hasText(config.getClientSecret())
                    ? passwordEncoder.encode(config.getClientSecret())
                    : null;
            if (secretHash == null) {
                log.info("First-party client '{}': no clientSecret — public 클라이언트로 등록합니다", config.getClientId());
            }

            Set<String> redirectUris = Arrays.stream(config.getRedirectUris().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            Set<String> scopes = StringUtils.hasText(config.getAllowedScopes())
                    ? Set.of(config.getAllowedScopes().split(" "))
                    : Set.of("profile", "email");

            // RegisteredClient 저장 (JdbcRegisteredClientRepository.save는 upsert — 시동 시 동기화)
            // FIRST_PARTY이므로 consent 생략
            RegisteredClient registeredClient = RegisteredClientFactory.build(
                    config.getClientId(), secretHash, config.getName(), redirectUris, scopes, false);
            registeredClientRepository.save(registeredClient);

            // 메타데이터 upsert
            ClientMetadata metadata = clientMetadataRepository.findById(config.getClientId())
                    .orElseGet(() -> ClientMetadata.builder().clientId(config.getClientId()).build());
            metadata.setDescription(config.getDescription());
            metadata.setFrontendUrl(config.getFrontendUrl());
            metadata.setClientType(ClientType.FIRST_PARTY);
            clientMetadataRepository.save(metadata);

            log.info("First-party client synchronized: {} (confidential={})", config.getClientId(), secretHash != null);
        }
    }
}
