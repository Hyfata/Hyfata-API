package kr.hyfata.rest.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientResponse {
    private Long id;
    private String clientId;
    private String clientSecret;
    private String name;
    private String description;
    private String frontendUrl;
    private List<String> redirectUris;
    private Boolean enabled;
    private Integer maxTokensPerUser;
    private String defaultScopes;
    private String allowedScopes;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
