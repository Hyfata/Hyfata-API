package kr.hyfata.rest.api.agora.chat.repository;

import kr.hyfata.rest.api.agora.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChat_IdOrderByIdDesc(Long chatId, Pageable pageable);

    List<Message> findByChat_IdAndIdLessThanOrderByIdDesc(Long chatId, Long cursor, Pageable pageable);

    List<Message> findByChat_IdAndIdGreaterThanOrderByIdAsc(Long chatId, Long cursor, Pageable pageable);

    List<Message> findBySender_Id(Long senderId);

    List<Message> findByChat_IdAndIsPinnedTrue(Long chatId);

    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND m.isDeleted = false ORDER BY m.id DESC")
    List<Message> findActiveMessagesByChatId(@Param("chatId") Long chatId, Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.isDeleted = true WHERE m.id = :messageId")
    int softDeleteById(@Param("messageId") Long messageId);

    long countByChat_IdAndIsDeletedFalse(Long chatId);
}
