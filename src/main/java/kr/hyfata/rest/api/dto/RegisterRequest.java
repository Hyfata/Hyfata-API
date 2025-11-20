package kr.hyfata.rest.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    private String email;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    @Builder.Default
    private String clientId = "default";  // OAuth 클라이언트 ID (기본값: default)
}