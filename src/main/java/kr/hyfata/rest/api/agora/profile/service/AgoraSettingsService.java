package kr.hyfata.rest.api.agora.profile.service;

import kr.hyfata.rest.api.auth.dto.agora.settings.NotificationSettingsResponse;
import kr.hyfata.rest.api.auth.dto.agora.settings.PrivacySettingsResponse;
import kr.hyfata.rest.api.auth.dto.agora.settings.UpdateNotificationSettingsRequest;
import kr.hyfata.rest.api.auth.dto.agora.settings.UpdatePrivacySettingsRequest;
import kr.hyfata.rest.api.auth.dto.agora.settings.UpdateBirthdayReminderRequest;

public interface AgoraSettingsService {

    NotificationSettingsResponse getNotificationSettings(String userEmail);

    NotificationSettingsResponse updateNotificationSettings(String userEmail, UpdateNotificationSettingsRequest request);

    PrivacySettingsResponse getPrivacySettings(String userEmail);

    PrivacySettingsResponse updatePrivacySettings(String userEmail, UpdatePrivacySettingsRequest request);

    NotificationSettingsResponse updateBirthdayReminder(String userEmail, UpdateBirthdayReminderRequest request);
}
