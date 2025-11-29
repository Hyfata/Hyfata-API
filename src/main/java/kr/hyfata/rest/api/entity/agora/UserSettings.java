package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import kr.hyfata.rest.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "user_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    public enum Visibility {
        PUBLIC, FRIENDS, NONE
    }

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    // 알림 설정
    @Column(nullable = false)
    @Builder.Default
    private Boolean pushEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean messageNotification = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean friendRequestNotification = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean teamNotification = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean noticeNotification = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean soundEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean vibrationEnabled = true;

    @Column
    private LocalTime doNotDisturbStart;

    @Column
    private LocalTime doNotDisturbEnd;

    // 프라이버시 설정
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Visibility profileVisibility = Visibility.FRIENDS;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Visibility phoneVisibility = Visibility.NONE;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Visibility birthdayVisibility = Visibility.FRIENDS;

    @Column(nullable = false)
    @Builder.Default
    private Boolean allowFriendRequests = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean allowGroupInvites = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean showOnlineStatus = true;

    // 보안 설정
    @Column(nullable = false)
    @Builder.Default
    private Boolean loginNotification = true;

    @Column
    @Builder.Default
    private Integer sessionTimeout = 30;

    // 생일 알림 설정
    @Column(nullable = false)
    @Builder.Default
    private Boolean birthdayReminderEnabled = true;

    @Column
    @Builder.Default
    private Integer birthdayReminderDaysBefore = 3;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
