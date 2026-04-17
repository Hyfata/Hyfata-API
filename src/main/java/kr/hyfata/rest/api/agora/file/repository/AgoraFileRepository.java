package kr.hyfata.rest.api.agora.file.repository;

import kr.hyfata.rest.api.agora.file.entity.AgoraFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgoraFileRepository extends JpaRepository<AgoraFile, Long> {

    List<AgoraFile> findByUploadedBy_IdOrderByCreatedAtDesc(Long userId);

    List<AgoraFile> findByUploadedBy_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AgoraFile> findByFileType(AgoraFile.FileType fileType);

    List<AgoraFile> findByUploadedBy_IdAndFileType(Long userId, AgoraFile.FileType fileType);

    long countByUploadedBy_Id(Long userId);
}
