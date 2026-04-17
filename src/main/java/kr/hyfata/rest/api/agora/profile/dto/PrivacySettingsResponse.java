package kr.hyfata.rest.api.agora.profile.dto;

import kr.hyfata.rest.api.agora.profile.entity.UserSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivacySettingsResponse {

    private String profileVisibility;
    private String phoneVisibility;
    private String birthdayVisibility;
    private Boolean allowFriendRequests;
    private Boolean allowGroupInvites;
    private Boolean showOnlineStatus;
    private Integer sessionTimeout;

    public static PrivacySettingsResponse from(UserSettings settings) {
        return PrivacySettingsResponse.builder()
                .profileVisibility(settings.getProfileVisibility() != null ? settings.getProfileVisibility().name() : null)
                .phoneVisibility(settings.getPhoneVisibility() != null ? settings.getPhoneVisibility().name() : null)
                .birthdayVisibility(settings.getBirthdayVisibility() != null ? settings.getBirthdayVisibility().name() : null)
                .allowFriendRequests(settings.getAllowFriendRequests())
                .allowGroupInvites(settings.getAllowGroupInvites())
                .showOnlineStatus(settings.getShowOnlineStatus())
                .sessionTimeout(settings.getSessionTimeout())
                .build();
    }
}
