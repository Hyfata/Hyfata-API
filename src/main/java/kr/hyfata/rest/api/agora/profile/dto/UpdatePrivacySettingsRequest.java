package kr.hyfata.rest.api.agora.profile.dto;

import lombok.Data;

@Data
public class UpdatePrivacySettingsRequest {
    private String profileVisibility;
    private String phoneVisibility;
    private String birthdayVisibility;
    private Boolean allowFriendRequests;
    private Boolean allowGroupInvites;
    private Boolean showOnlineStatus;
    private Integer sessionTimeout;
}
