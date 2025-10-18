package kr.hyfata.rest.api.repository;

import kr.hyfata.rest.api.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByClientId(String clientId);
    Optional<Client> findByClientIdAndClientSecret(String clientId, String clientSecret);
    boolean existsByClientId(String clientId);
}
