package kr.hyfata.rest.api.agora.chat.repository;

import kr.hyfata.rest.api.entity.agora.MessageReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, Long> {

    List<MessageReadStatus> findByMessage_Id(Long messageId);

    Optional<MessageReadStatus> findByMessage_IdAndUser_Id(Long messageId, Long userId);

    boolean existsByMessage_IdAndUser_Id(Long messageId, Long userId);

    long countByMessage_Id(Long messageId);

    @Query("SELECT mrs.message.id FROM MessageReadStatus mrs WHERE mrs.user.id = :userId AND mrs.message.chat.id = :chatId ORDER BY mrs.message.id DESC")
    List<Long> findReadMessageIdsByUserAndChat(@Param("userId") Long userId, @Param("chatId") Long chatId);
}
