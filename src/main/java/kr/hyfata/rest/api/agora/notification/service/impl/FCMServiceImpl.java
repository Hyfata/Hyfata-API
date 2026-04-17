package kr.hyfata.rest.api.service.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import kr.hyfata.rest.api.repository.agora.FcmTokenRepository;
import kr.hyfata.rest.api.service.FCMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FCMServiceImpl implements FCMService {

    private final FirebaseMessaging firebaseMessaging;
    private final FcmTokenRepository fcmTokenRepository;

    @Override
    public void sendNotification(String fcmToken, String title, String message) {
        if (firebaseMessaging == null || fcmToken == null || fcmToken.isEmpty()) {
            log.warn("Cannot send notification: FirebaseMessaging is null or token is empty");
            return;
        }

        try {
            Message msg = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(message)
                            .build())
                    .setToken(fcmToken)
                    .build();

            String response = firebaseMessaging.send(msg);
            log.info("Successfully sent notification to token: {}, messageId: {}", fcmToken, response);
        } catch (Exception e) {
            log.error("Failed to send notification to token {}: {}", fcmToken, e.getMessage());
        }
    }

    @Override
    public void sendNotificationToUser(Long userId, String title, String message) {
        try {
            List<String> tokens = fcmTokenRepository.findTokensByUserId(userId);
            for (String token : tokens) {
                sendNotification(token, title, message);
            }
        } catch (Exception e) {
            log.error("Failed to send notification to user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void sendNotificationToMultipleUsers(List<Long> userIds, String title, String message) {
        for (Long userId : userIds) {
            sendNotificationToUser(userId, title, message);
        }
    }
}
