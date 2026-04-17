package kr.hyfata.rest.api.agora.profile.service.impl;

import kr.hyfata.rest.api.auth.dto.agora.settings.NotificationSettingsResponse;
import kr.hyfata.rest.api.auth.dto.agora.settings.PrivacySettingsResponse;
import kr.hyfata.rest.api.auth.dto.agora.settings.UpdateNotificationSettingsRequest;
import kr.hyfata.rest.api.auth.dto.agora.settings.UpdatePrivacySettingsRequest;
import kr.hyfata.rest.api.auth.dto.agora.settings.UpdateBirthdayReminderRequest;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.entity.agora.UserSettings;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.UserSettingsRepository;
import kr.hyfata.rest.api.agora.profile.service.AgoraSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraSettingsServiceImpl implements AgoraSettingsService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Override
    public NotificationSettingsResponse getNotificationSettings(String userEmail) {
        User user = findUserByEmail(userEmail);
        UserSettings settings = findSettingsByUserId(user.getId());
        return NotificationSettingsResponse.from(settings);
    }

    @Override
    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(String userEmail, UpdateNotificationSettingsRequest request) {
        User user = findUserByEmail(userEmail);
        UserSettings settings = findSettingsByUserId(user.getId());

        if (request.getPushEnabled() != null) {
            settings.setPushEnabled(request.getPushEnabled());
        }

        if (request.getMessageNotification() != null) {
            settings.setMessageNotification(request.getMessageNotification());
        }

        if (request.getFriendRequestNotification() != null) {
            settings.setFriendRequestNotification(request.getFriendRequestNotification());
        }

        if (request.getTeamNotification() != null) {
            settings.setTeamNotification(request.getTeamNotification());
        }

        if (request.getNoticeNotification() != null) {
            settings.setNoticeNotification(request.getNoticeNotification());
        }

        if (request.getSoundEnabled() != null) {
            settings.setSoundEnabled(request.getSoundEnabled());
        }

        if (request.getVibrationEnabled() != null) {
            settings.setVibrationEnabled(request.getVibrationEnabled());
        }

        if (request.getDoNotDisturbStart() != null) {
            settings.setDoNotDisturbStart(request.getDoNotDisturbStart());
        }

        if (request.getDoNotDisturbEnd() != null) {
            settings.setDoNotDisturbEnd(request.getDoNotDisturbEnd());
        }

        if (request.getLoginNotification() != null) {
            settings.setLoginNotification(request.getLoginNotification());
        }

        UserSettings updated = userSettingsRepository.save(settings);
        return NotificationSettingsResponse.from(updated);
    }

    @Override
    public PrivacySettingsResponse getPrivacySettings(String userEmail) {
        User user = findUserByEmail(userEmail);
        UserSettings settings = findSettingsByUserId(user.getId());
        return PrivacySettingsResponse.from(settings);
    }

    @Override
    @Transactional
    public PrivacySettingsResponse updatePrivacySettings(String userEmail, UpdatePrivacySettingsRequest request) {
        User user = findUserByEmail(userEmail);
        UserSettings settings = findSettingsByUserId(user.getId());

        if (request.getProfileVisibility() != null) {
            settings.setProfileVisibility(UserSettings.Visibility.valueOf(request.getProfileVisibility().toUpperCase()));
        }

        if (request.getPhoneVisibility() != null) {
            settings.setPhoneVisibility(UserSettings.Visibility.valueOf(request.getPhoneVisibility().toUpperCase()));
        }

        if (request.getBirthdayVisibility() != null) {
            settings.setBirthdayVisibility(UserSettings.Visibility.valueOf(request.getBirthdayVisibility().toUpperCase()));
        }

        if (request.getAllowFriendRequests() != null) {
            settings.setAllowFriendRequests(request.getAllowFriendRequests());
        }

        if (request.getAllowGroupInvites() != null) {
            settings.setAllowGroupInvites(request.getAllowGroupInvites());
        }

        if (request.getShowOnlineStatus() != null) {
            settings.setShowOnlineStatus(request.getShowOnlineStatus());
        }

        if (request.getSessionTimeout() != null) {
            settings.setSessionTimeout(request.getSessionTimeout());
        }

        UserSettings updated = userSettingsRepository.save(settings);
        return PrivacySettingsResponse.from(updated);
    }

    @Override
    @Transactional
    public NotificationSettingsResponse updateBirthdayReminder(String userEmail, UpdateBirthdayReminderRequest request) {
        User user = findUserByEmail(userEmail);
        UserSettings settings = findSettingsByUserId(user.getId());

        if (request.getBirthdayReminderEnabled() != null) {
            settings.setBirthdayReminderEnabled(request.getBirthdayReminderEnabled());
        }

        if (request.getBirthdayReminderDaysBefore() != null) {
            settings.setBirthdayReminderDaysBefore(request.getBirthdayReminderDaysBefore());
        }

        UserSettings updated = userSettingsRepository.save(settings);
        return NotificationSettingsResponse.from(updated);
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private UserSettings findSettingsByUserId(Long userId) {
        return userSettingsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalStateException("사용자 설정을 찾을 수 없습니다"));
    }
}
