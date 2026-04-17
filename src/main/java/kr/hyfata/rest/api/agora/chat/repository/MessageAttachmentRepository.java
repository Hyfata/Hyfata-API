package kr.hyfata.rest.api.agora.chat.repository;

import kr.hyfata.rest.api.entity.agora.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

    List<MessageAttachment> findByMessage_IdOrderByOrderIndexAsc(Long messageId);

    List<MessageAttachment> findByFile_Id(Long fileId);

    void deleteByMessage_Id(Long messageId);
}
