package kr.hyfata.rest.api.dto.agora.team;

import kr.hyfata.rest.api.entity.agora.Todo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoResponse {

    private Long todoId;

    private Long teamId;

    private String createdByEmail;

    private String assignedToEmail;

    private String title;

    private String description;

    private String status;

    private String priority;

    private LocalDateTime dueDate;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static TodoResponse from(Todo todo) {
        return TodoResponse.builder()
                .todoId(todo.getId())
                .teamId(todo.getTeam().getId())
                .createdByEmail(todo.getCreatedBy().getEmail())
                .assignedToEmail(todo.getAssignedTo() != null ? todo.getAssignedTo().getEmail() : null)
                .title(todo.getTitle())
                .description(todo.getDescription())
                .status(todo.getStatus().toString())
                .priority(todo.getPriority().toString())
                .dueDate(todo.getDueDate())
                .completedAt(todo.getCompletedAt())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .build();
    }
}
