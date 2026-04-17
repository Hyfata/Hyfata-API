package kr.hyfata.rest.api.auth.repository;

import kr.hyfata.rest.api.auth.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}
