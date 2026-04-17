package kr.hyfata.rest.api.dto.agora.team;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTodoRequest {

    private String title;

    private String description;

    private Long assignedToId;

    private String priority;

    private LocalDateTime dueDate;
}
