package kr.hyfata.rest.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 로그아웃 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {

    /**
     * Refresh Token (현재 세션 로그아웃 시 필요)
     */
    private String refreshToken;

    /**
     * 모든 세션 로그아웃 여부
     * true: 모든 기기에서 로그아웃
     * false: 현재 세션만 로그아웃
     */
    @Builder.Default
    private Boolean logoutAll = false;
}
