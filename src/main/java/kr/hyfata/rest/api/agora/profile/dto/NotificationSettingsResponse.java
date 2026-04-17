package kr.hyfata.rest.api.agora.profile.dto;

import kr.hyfata.rest.api.agora.profile.entity.UserSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingsResponse {

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
    private Boolean birthdayReminderEnabled;
    private Integer birthdayReminderDaysBefore;

    public static NotificationSettingsResponse from(UserSettings settings) {
        return NotificationSettingsResponse.builder()
                .pushEnabled(settings.getPushEnabled())
                .messageNotification(settings.getMessageNotification())
                .friendRequestNotification(settings.getFriendRequestNotification())
                .teamNotification(settings.getTeamNotification())
                .noticeNotification(settings.getNoticeNotification())
                .soundEnabled(settings.getSoundEnabled())
                .vibrationEnabled(settings.getVibrationEnabled())
                .doNotDisturbStart(settings.getDoNotDisturbStart())
                .doNotDisturbEnd(settings.getDoNotDisturbEnd())
                .loginNotification(settings.getLoginNotification())
                .birthdayReminderEnabled(settings.getBirthdayReminderEnabled())
                .birthdayReminderDaysBefore(settings.getBirthdayReminderDaysBefore())
                .build();
    }
}
