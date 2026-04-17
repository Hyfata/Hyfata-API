package kr.hyfata.rest.api.dto.agora.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteMembersRequest {

    @NotEmpty(message = "memberAgoraIds must not be empty")
    private List<String> memberAgoraIds;
}
