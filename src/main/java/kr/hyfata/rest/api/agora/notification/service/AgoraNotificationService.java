package kr.hyfata.rest.api.agora.notification.service;

import kr.hyfata.rest.api.agora.notification.dto.NotificationResponse;

import java.util.List;

public interface AgoraNotificationService {

    List<NotificationResponse> getNotifications(String userEmail);

    long getUnreadCount(String userEmail);

    NotificationResponse markAsRead(String userEmail, Long notificationId);

    String markAllAsRead(String userEmail);

    String deleteNotification(String userEmail, Long notificationId);
}
