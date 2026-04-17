package kr.hyfata.rest.api.agora.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatRequest {

    @NotBlank(message = "targetAgoraId is required")
    private String targetAgoraId;
}
