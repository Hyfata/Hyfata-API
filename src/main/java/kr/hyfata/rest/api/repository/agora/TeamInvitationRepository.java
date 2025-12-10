package kr.hyfata.rest.api.repository.agora;

import kr.hyfata.rest.api.entity.agora.TeamInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {

    List<TeamInvitation> findByToUser_IdAndStatusOrderByCreatedAtDesc(Long toUserId, TeamInvitation.Status status);

    List<TeamInvitation> findByToUser_IdOrderByCreatedAtDesc(Long toUserId);

    List<TeamInvitation> findByTeam_IdOrderByCreatedAtDesc(Long teamId);

    List<TeamInvitation> findByTeam_IdAndStatusOrderByCreatedAtDesc(Long teamId, TeamInvitation.Status status);

    Optional<TeamInvitation> findByTeam_IdAndToUser_Id(Long teamId, Long toUserId);

    boolean existsByTeam_IdAndToUser_IdAndStatus(Long teamId, Long toUserId, TeamInvitation.Status status);

    long countByToUser_IdAndStatus(Long toUserId, TeamInvitation.Status status);
}
