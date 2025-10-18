package kr.hyfata.rest.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private Boolean twoFactorRequired;
    private String message;

    public static AuthResponse success(String accessToken, String refreshToken, Long expiresIn) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .twoFactorRequired(false)
                .message("Login successful")
                .build();
    }

    public static AuthResponse twoFactorRequired(String message) {
        return AuthResponse.builder()
                .twoFactorRequired(true)
                .message(message)
                .build();
    }
}