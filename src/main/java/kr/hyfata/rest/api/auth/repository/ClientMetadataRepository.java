package kr.hyfata.rest.api.auth.repository;

import kr.hyfata.rest.api.auth.entity.ClientMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientMetadataRepository extends JpaRepository<ClientMetadata, String> {
}
