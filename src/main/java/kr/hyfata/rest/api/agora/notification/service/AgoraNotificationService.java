package kr.hyfata.rest.api.agora.notification.service;

import kr.hyfata.rest.api.auth.dto.agora.NotificationResponse;
import kr.hyfata.rest.api.auth.dto.agora.RegisterFcmTokenRequest;

import java.util.List;

public interface AgoraNotificationService {

    List<NotificationResponse> getNotifications(String userEmail);

    long getUnreadCount(String userEmail);

    NotificationResponse markAsRead(String userEmail, Long notificationId);

    String markAllAsRead(String userEmail);

    String deleteNotification(String userEmail, Long notificationId);

    String registerFcmToken(String userEmail, RegisterFcmTokenRequest request);

    String unregisterFcmToken(String userEmail, String token);
}
