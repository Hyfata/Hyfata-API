package kr.hyfata.rest.api.dto.agora.team;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendTeamInvitationRequest {

    @NotBlank(message = "agoraId is required")
    private String agoraId;
}
