package kr.hyfata.rest.api.dto.agora.team;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamProfileRequest {

    @NotBlank(message = "displayName is required")
    private String displayName;

    private String profileImage;
}
