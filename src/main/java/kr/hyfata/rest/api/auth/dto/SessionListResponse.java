package kr.hyfata.rest.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 세션 목록 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionListResponse {

    /**
     * 총 활성 세션 수
     */
    private int totalSessions;

    /**
     * 세션 목록
     */
    private List<UserSessionDTO> sessions;

    public static SessionListResponse of(List<UserSessionDTO> sessions) {
        return SessionListResponse.builder()
                .totalSessions(sessions.size())
                .sessions(sessions)
                .build();
    }
}
