package kr.hyfata.rest.api.agora.team.repository;

import kr.hyfata.rest.api.agora.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeam_IdOrderByJoinedAtAsc(Long teamId);

    List<TeamMember> findByUser_Id(Long userId);

    Optional<TeamMember> findByTeam_IdAndUser_Id(Long teamId, Long userId);

    boolean existsByTeam_IdAndUser_Id(Long teamId, Long userId);

    void deleteByTeam_IdAndUser_Id(Long teamId, Long userId);

    long countByTeam_Id(Long teamId);

    List<TeamMember> findByTeam_IdAndRole_Id(Long teamId, Long roleId);
}
