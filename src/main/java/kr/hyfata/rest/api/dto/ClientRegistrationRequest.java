package kr.hyfata.rest.api.dto;

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
}
