package kr.hyfata.rest.api.client.dto;

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
    private Long ownerId;
    private String allowedScopes;   // 예: "profile email profile:write account:password" (관리자만 지정 가능)
}
