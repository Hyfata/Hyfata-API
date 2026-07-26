package kr.hyfata.rest.api.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestoreAccountRequest {

    private String email;

    @Builder.Default
    private String clientId = "default";
}
