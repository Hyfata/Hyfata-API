package kr.hyfata.rest.api.agora.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFcmTokenRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "deviceType is required")
    private String deviceType;

    private String deviceId;
}
