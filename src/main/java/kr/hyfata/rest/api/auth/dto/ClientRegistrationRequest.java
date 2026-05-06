package kr.hyfata.rest.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientRegistrationRequest {
    private String name;
    private String description;
    private String frontendUrl;
    private List<String> redirectUris;
    private Integer maxTokensPerUser;
    private Long ownerId;
    private String defaultScopes;   // 예: "profile email"
    private String allowedScopes;   // 예: "profile email profile:write account:password"
}
