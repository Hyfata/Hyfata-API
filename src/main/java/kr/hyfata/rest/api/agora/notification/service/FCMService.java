package kr.hyfata.rest.api.service;

public interface FCMService {

    void sendNotification(String fcmToken, String title, String message);

    void sendNotificationToUser(Long userId, String title, String message);

    void sendNotificationToMultipleUsers(java.util.List<Long> userIds, String title, String message);
}
