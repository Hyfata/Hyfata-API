package kr.hyfata.rest.api.dto.agora.team;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTodoRequest {

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    private String description;

    private Long assignedToId;

    private String priority;

    private LocalDateTime dueDate;
}
