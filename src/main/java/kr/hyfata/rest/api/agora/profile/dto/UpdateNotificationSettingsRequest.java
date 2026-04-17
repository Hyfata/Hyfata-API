package kr.hyfata.rest.api.agora.profile.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class UpdateNotificationSettingsRequest {
    private Boolean pushEnabled;
    private Boolean messageNotification;
    private Boolean friendRequestNotification;
    private Boolean teamNotification;
    private Boolean noticeNotification;
    private Boolean soundEnabled;
    private Boolean vibrationEnabled;
    private LocalTime doNotDisturbStart;
    private LocalTime doNotDisturbEnd;
    private Boolean loginNotification;
}
