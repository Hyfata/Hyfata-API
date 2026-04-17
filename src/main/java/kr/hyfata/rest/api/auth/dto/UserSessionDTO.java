package kr.hyfata.rest.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 세션 정보 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionDTO {

    /**
     * 세션 ID (Refresh Token 해시)
     */
    private String sessionId;

    /**
     * 디바이스 타입 (Desktop, Mobile, Tablet)
     */
    private String deviceType;

    /**
     * 디바이스 이름 (예: "Chrome on Windows")
     */
    private String deviceName;

    /**
     * IP 주소
     */
    private String ipAddress;

    /**
     * 위치 (예: "Seoul, South Korea")
     */
    private String location;

    /**
     * 마지막 활동 시간
     */
    private LocalDateTime lastActiveAt;

    /**
     * 세션 생성 시간
     */
    private LocalDateTime createdAt;

    /**
     * 세션 만료 시간
     */
    private LocalDateTime expiresAt;

    /**
     * 현재 세션 여부
     */
    private Boolean isCurrent;
}
