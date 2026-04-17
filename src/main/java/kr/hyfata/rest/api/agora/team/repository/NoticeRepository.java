package kr.hyfata.rest.api.agora.team.repository;

import kr.hyfata.rest.api.agora.team.entity.Notice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByTeam_IdOrderByCreatedAtDesc(Long teamId);

    List<Notice> findByTeam_IdOrderByCreatedAtDesc(Long teamId, Pageable pageable);

    List<Notice> findByAuthor_Id(Long authorId);

    long countByTeam_Id(Long teamId);
}
