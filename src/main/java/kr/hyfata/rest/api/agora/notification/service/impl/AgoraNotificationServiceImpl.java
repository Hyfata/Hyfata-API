package kr.hyfata.rest.api.agora.notification.service.impl;

import kr.hyfata.rest.api.agora.notification.dto.NotificationResponse;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.agora.notification.entity.Notification;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.agora.notification.repository.NotificationRepository;
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

    @Override
    public List<NotificationResponse> getNotifications(String userEmail) {
        User user = findUserByEmail(userEmail);
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

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to update this notification");
        }

        notification.setIsRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public String markAllAsRead(String userEmail) {
        User user = findUserByEmail(userEmail);
        List<Notification> unread = notificationRepository.findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(user.getId());
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        return "All notifications marked as read";
    }

    @Override
    @Transactional
    public String deleteNotification(String userEmail, Long notificationId) {
        User user = findUserByEmail(userEmail);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to delete this notification");
        }

        notificationRepository.deleteById(notificationId);
        return "Notification deleted";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
