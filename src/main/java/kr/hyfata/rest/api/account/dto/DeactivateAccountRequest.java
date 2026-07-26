package kr.hyfata.rest.api.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeactivateAccountRequest {

    private String password;

    private String reason;
}
