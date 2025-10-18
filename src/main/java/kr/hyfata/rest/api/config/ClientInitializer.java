package kr.hyfata.rest.api.config;

import kr.hyfata.rest.api.entity.Client;
import kr.hyfata.rest.api.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 기본 OAuth 클라이언트 초기화
 * 애플리케이션 시작 시 기본 클라이언트가 없으면 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientInitializer implements CommandLineRunner {

    private final ClientRepository clientRepository;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String defaultFrontendUrl;

    @Override
    public void run(String... args) throws Exception {
        try {
            // 기본 클라이언트 확인
            if (!clientRepository.existsByClientId("default")) {
                log.info("Default client not found. Creating default client...");

                Client defaultClient = Client.builder()
                        .clientId("default")
                        .clientSecret("default-secret-key-change-this-in-production")
                        .name("Default Client")
                        .description("Default OAuth client for local development")
                        .frontendUrl(defaultFrontendUrl)
                        .redirectUris("http://localhost:3000,http://localhost:3001")
                        .enabled(true)
                        .maxTokensPerUser(5)
                        .build();

                clientRepository.save(defaultClient);
                log.info("Default client created successfully with clientId: 'default'");
            } else {
                log.info("Default client already exists");
            }
        } catch (Exception e) {
            log.error("Error initializing default client: {}", e.getMessage(), e);
        }
    }
}
