package kr.hyfata.rest.api.dto.agora.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendRequestDto {

    @NotBlank(message = "agoraId is required")
    private String agoraId;
}
