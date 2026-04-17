package kr.hyfata.rest.api.common.config;

import kr.hyfata.rest.api.auth.entity.Client;
import kr.hyfata.rest.api.auth.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Client secrets encryption initializer
 * On application startup, this will encrypt any plaintext client secrets
 * This ensures backward compatibility while securing new clients
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientSecretEncryptionInitializer implements ApplicationRunner {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Checking for unencrypted client secrets...");

        List<Client> clients = clientRepository.findAll();
        int encryptedCount = 0;

        for (Client client : clients) {
            // Skip if already encrypted (BCrypt hashes start with $2a$, $2b$, or $2y$)
            if (client.getClientSecret() != null && client.getClientSecret().startsWith("$2")) {
                continue;
            }

            // Encrypt plaintext clientSecret
            if (client.getClientSecret() != null && !client.getClientSecret().isEmpty()) {
                String encryptedSecret = passwordEncoder.encode(client.getClientSecret());
                client.setClientSecret(encryptedSecret);
                clientRepository.save(client);
                encryptedCount++;
                log.debug("Encrypted clientSecret for client: {}", client.getClientId());
            }
        }

        if (encryptedCount > 0) {
            log.info("Encrypted {} client secrets", encryptedCount);
        } else {
            log.debug("All client secrets are already encrypted");
        }
    }
}
