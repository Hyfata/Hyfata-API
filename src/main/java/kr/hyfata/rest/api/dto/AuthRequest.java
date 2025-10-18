package kr.hyfata.rest.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {
    private String email;
    private String password;
    @Builder.Default
    private String clientId = "default";  // OAuth 클라이언트 ID (기본값: default)
}