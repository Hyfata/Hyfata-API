package kr.hyfata.rest.api.agora.file.repository;

import kr.hyfata.rest.api.agora.file.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Optional<FileMetadata> findByFile_Id(Long fileId);
}
