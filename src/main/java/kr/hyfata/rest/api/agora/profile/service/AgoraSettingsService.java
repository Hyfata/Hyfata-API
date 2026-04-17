package kr.hyfata.rest.api.agora.profile.service;

import kr.hyfata.rest.api.agora.profile.dto.NotificationSettingsResponse;
import kr.hyfata.rest.api.agora.profile.dto.PrivacySettingsResponse;
import kr.hyfata.rest.api.agora.profile.dto.UpdateNotificationSettingsRequest;
import kr.hyfata.rest.api.agora.profile.dto.UpdatePrivacySettingsRequest;
import kr.hyfata.rest.api.agora.profile.dto.UpdateBirthdayReminderRequest;

public interface AgoraSettingsService {

    NotificationSettingsResponse getNotificationSettings(String userEmail);

    NotificationSettingsResponse updateNotificationSettings(String userEmail, UpdateNotificationSettingsRequest request);

    PrivacySettingsResponse getPrivacySettings(String userEmail);

    PrivacySettingsResponse updatePrivacySettings(String userEmail, UpdatePrivacySettingsRequest request);

    NotificationSettingsResponse updateBirthdayReminder(String userEmail, UpdateBirthdayReminderRequest request);
}
