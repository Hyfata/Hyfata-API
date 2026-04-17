package kr.hyfata.rest.api.agora.profile.dto;

import lombok.Data;

@Data
public class UpdateBirthdayReminderRequest {
    private Boolean birthdayReminderEnabled;
    private Integer birthdayReminderDaysBefore;
}
