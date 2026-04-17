package kr.hyfata.rest.api.agora.chat.repository;

import kr.hyfata.rest.api.entity.agora.ChatFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatFolderRepository extends JpaRepository<ChatFolder, Long> {

    List<ChatFolder> findByUser_IdOrderByOrderIndexAsc(Long userId);

    Optional<ChatFolder> findByUser_IdAndName(Long userId, String name);

    boolean existsByUser_IdAndName(Long userId, String name);

    long countByUser_Id(Long userId);
}
