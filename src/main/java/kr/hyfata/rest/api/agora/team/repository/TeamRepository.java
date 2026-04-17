package kr.hyfata.rest.api.agora.team.repository;

import kr.hyfata.rest.api.agora.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByCreatedBy_Id(Long userId);

    Optional<Team> findByCreatedBy_IdAndIsMainTrue(Long userId);

    @Query("SELECT t FROM Team t JOIN t.members m WHERE m.user.id = :userId ORDER BY t.createdAt DESC")
    List<Team> findTeamsByUserId(@Param("userId") Long userId);

    List<Team> findByNameContaining(String name);
}
