package kr.hyfata.rest.api.agora.notification.service.impl;

import kr.hyfata.rest.api.auth.dto.agora.NotificationResponse;
import kr.hyfata.rest.api.auth.dto.agora.RegisterFcmTokenRequest;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.entity.agora.Notification;
import kr.hyfata.rest.api.entity.agora.FcmToken;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.NotificationRepository;
import kr.hyfata.rest.api.repository.agora.FcmTokenRepository;
import kr.hyfata.rest.api.agora.notification.service.AgoraNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraNotificationServiceImpl implements AgoraNotificationService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository fcmTokenRepository;

    @Override
    public List<NotificationResponse> getNotifications(String userEmail) {
        User user = findUserByEmail(userEmail);

        // 최근 알림 100개 조회
        Pageable pageable = PageRequest.of(0, 100);
        List<Notification> notifications = notificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId(), pageable);

        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(String userEmail) {
        User user = findUserByEmail(userEmail);
        return notificationRepository.countByUser_IdAndIsReadFalse(user.getId());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(String userEmail, Long notificationId) {
        User user = findUserByEmail(userEmail);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        // Verify ownership
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to update this notification");
        }

        notification.setIsRead(true);
        Notification updated = notificationRepository.save(notification);

        return NotificationResponse.from(updated);
    }

    @Override
    @Transactional
    public String markAllAsRead(String userEmail) {
        User user = findUserByEmail(userEmail);

        List<Notification> unreadNotifications = notificationRepository.findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(user.getId());

        for (Notification notification : unreadNotifications) {
            notification.setIsRead(true);
        }

        notificationRepository.saveAll(unreadNotifications);

        return "All notifications marked as read";
    }

    @Override
    @Transactional
    public String deleteNotification(String userEmail, Long notificationId) {
        User user = findUserByEmail(userEmail);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        // Verify ownership
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to delete this notification");
        }

        notificationRepository.deleteById(notificationId);
        return "Notification deleted";
    }

    @Override
    @Transactional
    public String registerFcmToken(String userEmail, RegisterFcmTokenRequest request) {
        User user = findUserByEmail(userEmail);

        // Check if token already exists
        if (fcmTokenRepository.existsByToken(request.getToken())) {
            // Update if already exists
            FcmToken existingToken = fcmTokenRepository.findByToken(request.getToken())
                    .orElse(null);
            if (existingToken != null) {
                existingToken.setUser(user);
                existingToken.setDeviceType(FcmToken.DeviceType.valueOf(request.getDeviceType().toUpperCase()));
                existingToken.setDeviceId(request.getDeviceId());
                fcmTokenRepository.save(existingToken);
                return "FCM token registered/updated";
            }
        }

        // Create new token
        FcmToken fcmToken = FcmToken.builder()
                .user(user)
                .token(request.getToken())
                .deviceType(FcmToken.DeviceType.valueOf(request.getDeviceType().toUpperCase()))
                .deviceId(request.getDeviceId())
                .build();

        fcmTokenRepository.save(fcmToken);
        return "FCM token registered";
    }

    @Override
    @Transactional
    public String unregisterFcmToken(String userEmail, String token) {
        User user = findUserByEmail(userEmail);

        FcmToken fcmToken = fcmTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("FCM token not found"));

        // Verify ownership
        if (!fcmToken.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to delete this token");
        }

        fcmTokenRepository.deleteById(fcmToken.getId());
        return "FCM token unregistered";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
