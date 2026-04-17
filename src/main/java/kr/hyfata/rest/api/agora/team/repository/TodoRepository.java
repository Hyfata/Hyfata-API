package kr.hyfata.rest.api.agora.team.repository;

import kr.hyfata.rest.api.entity.agora.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findByTeam_IdOrderByDueDateAscCreatedAtDesc(Long teamId);

    List<Todo> findByTeam_IdAndStatus(Long teamId, Todo.Status status);

    List<Todo> findByAssignedTo_Id(Long userId);

    List<Todo> findByAssignedTo_IdAndStatus(Long userId, Todo.Status status);

    List<Todo> findByTeam_IdAndDueDateBefore(Long teamId, LocalDateTime dueDate);

    List<Todo> findByTeam_IdAndPriority(Long teamId, Todo.Priority priority);

    long countByTeam_IdAndStatus(Long teamId, Todo.Status status);
}
