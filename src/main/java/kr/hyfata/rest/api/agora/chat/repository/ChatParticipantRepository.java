package kr.hyfata.rest.api.agora.chat.repository;

import kr.hyfata.rest.api.agora.chat.entity.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    List<ChatParticipant> findByChat_Id(Long chatId);

    List<ChatParticipant> findByUser_IdOrderByIsPinnedDescPinnedAtDesc(Long userId);

    List<ChatParticipant> findByUser_IdAndIsPinnedTrue(Long userId);

    Optional<ChatParticipant> findByChat_IdAndUser_Id(Long chatId, Long userId);

    boolean existsByChat_IdAndUser_Id(Long chatId, Long userId);

    void deleteByChat_IdAndUser_Id(Long chatId, Long userId);

    long countByChat_Id(Long chatId);

    List<ChatParticipant> findByChat_IdAndRole(Long chatId, ChatParticipant.Role role);
}
