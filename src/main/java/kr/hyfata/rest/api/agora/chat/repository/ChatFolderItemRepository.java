package kr.hyfata.rest.api.agora.chat.repository;

import kr.hyfata.rest.api.agora.chat.entity.ChatFolderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatFolderItemRepository extends JpaRepository<ChatFolderItem, Long> {

    List<ChatFolderItem> findByFolder_IdOrderByCreatedAtDesc(Long folderId);

    List<ChatFolderItem> findByChat_Id(Long chatId);

    Optional<ChatFolderItem> findByFolder_IdAndChat_Id(Long folderId, Long chatId);

    boolean existsByFolder_IdAndChat_Id(Long folderId, Long chatId);

    void deleteByFolder_IdAndChat_Id(Long folderId, Long chatId);

    void deleteByFolder_Id(Long folderId);
}
