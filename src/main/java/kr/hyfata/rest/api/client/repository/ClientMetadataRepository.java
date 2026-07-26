package kr.hyfata.rest.api.client.repository;

import kr.hyfata.rest.api.client.entity.ClientMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientMetadataRepository extends JpaRepository<ClientMetadata, String> {
}
