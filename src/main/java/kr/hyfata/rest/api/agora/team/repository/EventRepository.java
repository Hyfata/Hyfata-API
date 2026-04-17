package kr.hyfata.rest.api.agora.team.repository;

import kr.hyfata.rest.api.agora.team.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByTeam_IdOrderByStartTimeAsc(Long teamId);

    List<Event> findByCreatedBy_Id(Long userId);

    @Query("SELECT e FROM Event e WHERE e.team.id = :teamId AND e.startTime >= :start AND e.endTime <= :end ORDER BY e.startTime ASC")
    List<Event> findByTeamIdAndDateRange(@Param("teamId") Long teamId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    @Query("SELECT e FROM Event e WHERE e.team.id = :teamId AND e.startTime >= :now ORDER BY e.startTime ASC")
    List<Event> findUpcomingEventsByTeamId(@Param("teamId") Long teamId, @Param("now") LocalDateTime now);

    long countByTeam_Id(Long teamId);
}
