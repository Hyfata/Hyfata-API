package kr.hyfata.rest.api.agora.team.repository;

import kr.hyfata.rest.api.entity.agora.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRoleRepository extends JpaRepository<TeamRole, Long> {

    List<TeamRole> findByTeam_IdOrderByCreatedAtAsc(Long teamId);

    Optional<TeamRole> findByTeam_IdAndName(Long teamId, String name);

    boolean existsByTeam_IdAndName(Long teamId, String name);

    void deleteByTeam_Id(Long teamId);
}
