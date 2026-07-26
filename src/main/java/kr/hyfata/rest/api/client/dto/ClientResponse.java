package kr.hyfata.rest.api.client.dto;

import kr.hyfata.rest.api.client.entity.ClientType;
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
    private String clientId;
    private String clientSecret;  // 등록 응답 시에만 평문 1회 포함
    private String name;
    private String description;
    private String frontendUrl;
    private List<String> redirectUris;
    private String allowedScopes;
    private ClientType clientType;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
