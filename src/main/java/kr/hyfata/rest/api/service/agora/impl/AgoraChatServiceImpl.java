package kr.hyfata.rest.api.service.agora.impl;

import kr.hyfata.rest.api.dto.agora.chat.ChatResponse;
import kr.hyfata.rest.api.dto.agora.chat.ChatListResponse;
import kr.hyfata.rest.api.dto.agora.chat.CreateChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.CreateDirectChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.CreateGroupChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.MessageDto;
import kr.hyfata.rest.api.dto.agora.chat.ParticipantProfile;
import kr.hyfata.rest.api.dto.agora.chat.SendMessageRequest;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.agora.Chat;
import kr.hyfata.rest.api.entity.agora.ChatParticipant;
import kr.hyfata.rest.api.entity.agora.Message;
import kr.hyfata.rest.api.entity.agora.MessageReadStatus;
import kr.hyfata.rest.api.entity.agora.AgoraUserProfile;
import kr.hyfata.rest.api.entity.agora.Team;
import kr.hyfata.rest.api.entity.agora.TeamProfile;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.AgoraUserProfileRepository;
import kr.hyfata.rest.api.repository.agora.ChatRepository;
import kr.hyfata.rest.api.repository.agora.ChatParticipantRepository;
import kr.hyfata.rest.api.repository.agora.MessageRepository;
import kr.hyfata.rest.api.repository.agora.MessageReadStatusRepository;
import kr.hyfata.rest.api.repository.agora.TeamRepository;
import kr.hyfata.rest.api.repository.agora.TeamProfileRepository;
import kr.hyfata.rest.api.service.agora.AgoraChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraChatServiceImpl implements AgoraChatService {

    private final UserRepository userRepository;
    private final AgoraUserProfileRepository agoraUserProfileRepository;
    private final ChatRepository chatRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final MessageRepository messageRepository;
    private final MessageReadStatusRepository messageReadStatusRepository;
    private final TeamRepository teamRepository;
    private final TeamProfileRepository teamProfileRepository;

    @Override
    public List<ChatListResponse> getChatList(String userEmail) {
        User user = findUserByEmail(userEmail);
        List<Chat> chats = chatRepository.findChatsByUserId(user.getId());

        return chats.stream()
                .map(chat -> {
                    Long participantCount = chatParticipantRepository.countByChat_Id(chat.getId());
                    List<Message> messages = messageRepository.findByChat_IdOrderByIdDesc(chat.getId(), PageRequest.of(0, 1));
                    Message lastMessage = messages.isEmpty() ? null : messages.get(0);

                    AgoraUserProfile senderProfile = null;
                    if (lastMessage != null) {
                        senderProfile = agoraUserProfileRepository.findById(lastMessage.getSender().getId()).orElse(null);
                    }

                    ChatParticipant chatParticipant = chatParticipantRepository.findByChat_IdAndUser_Id(chat.getId(), user.getId())
                            .orElse(null);
                    Boolean isPinned = chatParticipant != null ? chatParticipant.getIsPinned() : false;
                    java.time.LocalDateTime pinnedAt = chatParticipant != null ? chatParticipant.getPinnedAt() : null;

                    return ChatListResponse.from(chat, lastMessage, participantCount, isPinned, pinnedAt, senderProfile);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatResponse createChat(String userEmail, CreateChatRequest request) {
        User fromUser = findUserByEmail(userEmail);

        AgoraUserProfile targetProfile = agoraUserProfileRepository.findByAgoraId(request.getTargetAgoraId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with agoraId: " + request.getTargetAgoraId()));

        User toUser = targetProfile.getUser();

        // Check if direct chat already exists between these users (FRIEND context)
        Optional<Chat> existingChat = chatRepository.findFriendDirectChatBetweenUsers(fromUser.getId(), toUser.getId());
        if (existingChat.isPresent()) {
            return convertToResponse(existingChat.get(), fromUser.getId());
        }

        // Create new direct chat
        Chat chat = Chat.builder()
                .type(Chat.ChatType.DIRECT)
                .context(Chat.ChatContext.FRIEND)
                .createdBy(fromUser)
                .readEnabled(true)
                .build();

        Chat savedChat = chatRepository.save(chat);

        // Add participants
        ChatParticipant participant1 = ChatParticipant.builder()
                .chat(savedChat)
                .user(fromUser)
                .role(ChatParticipant.Role.MEMBER)
                .build();

        ChatParticipant participant2 = ChatParticipant.builder()
                .chat(savedChat)
                .user(toUser)
                .role(ChatParticipant.Role.MEMBER)
                .build();

        chatParticipantRepository.save(participant1);
        chatParticipantRepository.save(participant2);

        return convertToResponse(savedChat, fromUser.getId());
    }

    @Override
    public ChatResponse getChatDetail(String userEmail, Long chatId) {
        User user = findUserByEmail(userEmail);

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        // Verify user is participant
        if (!chatParticipantRepository.existsByChat_IdAndUser_Id(chatId, user.getId())) {
            throw new IllegalStateException("User is not a participant of this chat");
        }

        return convertToResponse(chat, user.getId());
    }

    @Override
    public List<MessageDto> getMessages(String userEmail, Long chatId, Long cursor, int limit) {
        User user = findUserByEmail(userEmail);

        // Verify user is participant
        if (!chatParticipantRepository.existsByChat_IdAndUser_Id(chatId, user.getId())) {
            throw new IllegalStateException("User is not a participant of this chat");
        }

        Pageable pageable = PageRequest.of(0, limit);
        List<Message> messages;

        if (cursor == null) {
            messages = messageRepository.findByChat_IdOrderByIdDesc(chatId, pageable);
        } else {
            messages = messageRepository.findByChat_IdAndIdLessThanOrderByIdDesc(chatId, cursor, pageable);
        }

        return messages.stream()
                .map(message -> {
                    AgoraUserProfile senderProfile = agoraUserProfileRepository.findById(message.getSender().getId())
                            .orElse(null);
                    return MessageDto.from(message, senderProfile);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageDto sendMessage(String userEmail, Long chatId, SendMessageRequest request) {
        User sender = findUserByEmail(userEmail);

        // Verify user is participant
        if (!chatParticipantRepository.existsByChat_IdAndUser_Id(chatId, sender.getId())) {
            throw new IllegalStateException("User is not a participant of this chat");
        }

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        Message.MessageType messageType = Message.MessageType.TEXT;
        if (request.getType() != null) {
            try {
                messageType = Message.MessageType.valueOf(request.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                messageType = Message.MessageType.TEXT;
            }
        }

        Message message = Message.builder()
                .chat(chat)
                .sender(sender)
                .content(request.getContent())
                .type(messageType)
                .build();

        Message savedMessage = messageRepository.save(message);

        // Update lastMessageAt
        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);

        // Auto-mark as read for sender
        MessageReadStatus readStatus = MessageReadStatus.builder()
                .message(savedMessage)
                .user(sender)
                .build();
        messageReadStatusRepository.save(readStatus);

        AgoraUserProfile senderProfile = agoraUserProfileRepository.findById(sender.getId()).orElse(null);
        return MessageDto.from(savedMessage, senderProfile);
    }

    @Override
    @Transactional
    public String deleteMessage(String userEmail, Long chatId, Long messageId) {
        User user = findUserByEmail(userEmail);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        // Verify user is message sender
        if (!message.getSender().getId().equals(user.getId())) {
            throw new IllegalStateException("You can only delete your own messages");
        }

        // Soft delete
        messageRepository.softDeleteById(messageId);

        return "Message deleted";
    }

    @Override
    @Transactional
    public String markChatAsRead(String userEmail, Long chatId) {
        User user = findUserByEmail(userEmail);

        // Verify user is participant
        if (!chatParticipantRepository.existsByChat_IdAndUser_Id(chatId, user.getId())) {
            throw new IllegalStateException("User is not a participant of this chat");
        }

        // Get all messages in chat that haven't been read by user
        List<Message> messages = messageRepository.findByChat_IdOrderByIdDesc(chatId, PageRequest.of(0, Integer.MAX_VALUE));

        for (Message message : messages) {
            if (!messageReadStatusRepository.existsByMessage_IdAndUser_Id(message.getId(), user.getId())) {
                MessageReadStatus readStatus = MessageReadStatus.builder()
                        .message(message)
                        .user(user)
                        .build();
                messageReadStatusRepository.save(readStatus);
            }
        }

        return "Chat marked as read";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private List<String> getParticipantAgoraIds(Chat chat) {
        return chat.getParticipants().stream()
                .map(participant -> {
                    AgoraUserProfile profile = agoraUserProfileRepository.findById(participant.getUser().getId())
                            .orElse(null);
                    return profile != null ? profile.getAgoraId() : "";
                })
                .collect(Collectors.toList());
    }

    // ==================== 새로운 컨텍스트 기반 메서드 ====================

    @Override
    @Transactional
    public ChatResponse getOrCreateDirectChat(String userEmail, CreateDirectChatRequest request) {
        User fromUser = findUserByEmail(userEmail);
        User toUser = userRepository.findById(request.getTargetUserId())
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        // 같은 사용자에게 채팅 생성 불가
        if (fromUser.getId().equals(toUser.getId())) {
            throw new IllegalArgumentException("Cannot create chat with yourself");
        }

        Chat.ChatContext context = request.getContext();

        // 기존 채팅 확인
        Optional<Chat> existingChat;
        if (context == Chat.ChatContext.TEAM) {
            if (request.getTeamId() == null) {
                throw new IllegalArgumentException("Team ID is required for TEAM context");
            }
            existingChat = chatRepository.findTeamDirectChatBetweenUsers(
                    request.getTeamId(), fromUser.getId(), toUser.getId());
        } else {
            existingChat = chatRepository.findFriendDirectChatBetweenUsers(fromUser.getId(), toUser.getId());
        }

        if (existingChat.isPresent()) {
            return convertToResponse(existingChat.get(), fromUser.getId());
        }

        // 새 채팅 생성
        Chat.ChatBuilder chatBuilder = Chat.builder()
                .type(Chat.ChatType.DIRECT)
                .context(context)
                .createdBy(fromUser)
                .readEnabled(true);

        if (context == Chat.ChatContext.TEAM) {
            Team team = teamRepository.findById(request.getTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("Team not found"));
            chatBuilder.team(team);
        }

        Chat savedChat = chatRepository.save(chatBuilder.build());

        // 참여자 추가
        chatParticipantRepository.save(ChatParticipant.builder()
                .chat(savedChat)
                .user(fromUser)
                .role(ChatParticipant.Role.MEMBER)
                .build());

        chatParticipantRepository.save(ChatParticipant.builder()
                .chat(savedChat)
                .user(toUser)
                .role(ChatParticipant.Role.MEMBER)
                .build());

        return convertToResponse(savedChat, fromUser.getId());
    }

    @Override
    @Transactional
    public ChatResponse createGroupChat(String userEmail, CreateGroupChatRequest request) {
        User creator = findUserByEmail(userEmail);

        // 친구 그룹 채팅만 지원 (팀 그룹 채팅은 createTeamGroupChat 사용)
        if (request.getContext() == Chat.ChatContext.TEAM) {
            throw new IllegalArgumentException("Use team API to access team group chat");
        }

        Chat chat = Chat.builder()
                .type(Chat.ChatType.GROUP)
                .context(Chat.ChatContext.FRIEND)
                .name(request.getName())
                .profileImage(request.getProfileImage())
                .createdBy(creator)
                .readEnabled(true)
                .build();

        Chat savedChat = chatRepository.save(chat);

        // 생성자를 ADMIN으로 추가
        chatParticipantRepository.save(ChatParticipant.builder()
                .chat(savedChat)
                .user(creator)
                .role(ChatParticipant.Role.ADMIN)
                .build());

        // 멤버 추가 (agoraId로)
        if (request.getMemberAgoraIds() != null && !request.getMemberAgoraIds().isEmpty()) {
            for (String agoraId : request.getMemberAgoraIds()) {
                AgoraUserProfile profile = agoraUserProfileRepository.findByAgoraId(agoraId).orElse(null);
                if (profile != null && !profile.getUser().getId().equals(creator.getId())) {
                    chatParticipantRepository.save(ChatParticipant.builder()
                            .chat(savedChat)
                            .user(profile.getUser())
                            .role(ChatParticipant.Role.MEMBER)
                            .build());
                }
            }
        }

        // 멤버 추가 (userId로)
        if (request.getMemberUserIds() != null && !request.getMemberUserIds().isEmpty()) {
            for (Long userId : request.getMemberUserIds()) {
                if (!userId.equals(creator.getId())) {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        chatParticipantRepository.save(ChatParticipant.builder()
                                .chat(savedChat)
                                .user(user)
                                .role(ChatParticipant.Role.MEMBER)
                                .build());
                    }
                }
            }
        }

        return convertToResponse(savedChat, creator.getId());
    }

    @Override
    @Transactional
    public ChatResponse createTeamGroupChat(Long teamId, Long creatorUserId) {
        // 팀당 하나의 그룹 채팅만 허용
        if (chatRepository.existsByTeam_IdAndTypeAndContext(teamId, Chat.ChatType.GROUP, Chat.ChatContext.TEAM)) {
            throw new IllegalStateException("Team group chat already exists");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Chat chat = Chat.builder()
                .type(Chat.ChatType.GROUP)
                .context(Chat.ChatContext.TEAM)
                .team(team)
                .name(team.getName())
                .profileImage(team.getProfileImage())
                .createdBy(creator)
                .readEnabled(true)
                .build();

        Chat savedChat = chatRepository.save(chat);

        // 팀 멤버들을 채팅 참여자로 추가
        for (var member : team.getMembers()) {
            ChatParticipant.Role role = member.getUser().getId().equals(creatorUserId)
                    ? ChatParticipant.Role.ADMIN
                    : ChatParticipant.Role.MEMBER;

            chatParticipantRepository.save(ChatParticipant.builder()
                    .chat(savedChat)
                    .user(member.getUser())
                    .role(role)
                    .build());
        }

        return convertToResponse(savedChat, creatorUserId);
    }

    @Override
    public List<ChatResponse> getDirectChatsByContext(String userEmail, Chat.ChatContext context) {
        User user = findUserByEmail(userEmail);

        List<Chat> chats = chatRepository.findChatsByUserIdAndTypeAndContext(
                user.getId(), Chat.ChatType.DIRECT, context);

        return chats.stream()
                .map(chat -> convertToResponse(chat, user.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatResponse> getGroupChats(String userEmail) {
        User user = findUserByEmail(userEmail);

        // 친구 그룹 채팅만 반환 (팀 그룹 채팅은 팀 API로 접근)
        List<Chat> chats = chatRepository.findChatsByUserIdAndTypeAndContext(
                user.getId(), Chat.ChatType.GROUP, Chat.ChatContext.FRIEND);

        return chats.stream()
                .map(chat -> convertToResponse(chat, user.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public ChatResponse getTeamGroupChat(Long teamId, String userEmail) {
        User user = findUserByEmail(userEmail);

        Chat chat = chatRepository.findByTeam_IdAndTypeAndContext(
                        teamId, Chat.ChatType.GROUP, Chat.ChatContext.TEAM)
                .orElseThrow(() -> new IllegalArgumentException("Team group chat not found"));

        // 참여자 검증
        if (!chatParticipantRepository.existsByChat_IdAndUser_Id(chat.getId(), user.getId())) {
            throw new IllegalStateException("User is not a participant of this chat");
        }

        return convertToResponse(chat, user.getId());
    }

    @Override
    public ChatResponse getChatDetailWithProfiles(String userEmail, Long chatId) {
        return getChatDetail(userEmail, chatId);
    }

    // ==================== 헬퍼 메서드 ====================

    private ChatResponse convertToResponse(Chat chat, Long requestingUserId) {
        String displayName;
        String displayImage;
        ParticipantProfile otherParticipant = null;

        if (chat.getType() == Chat.ChatType.DIRECT) {
            // 1:1 채팅: 상대방 프로필에서 이름/이미지 조회
            ChatParticipant other = chat.getParticipants().stream()
                    .filter(p -> !p.getUser().getId().equals(requestingUserId))
                    .findFirst()
                    .orElse(null);

            if (other != null) {
                Long otherUserId = other.getUser().getId();

                if (chat.getContext() == Chat.ChatContext.FRIEND) {
                    AgoraUserProfile profile = agoraUserProfileRepository.findById(otherUserId).orElse(null);
                    if (profile != null) {
                        displayName = profile.getDisplayName();
                        displayImage = profile.getProfileImage();
                        otherParticipant = ParticipantProfile.builder()
                                .userId(otherUserId)
                                .displayName(profile.getDisplayName())
                                .profileImage(profile.getProfileImage())
                                .identifier(profile.getAgoraId())
                                .build();
                    } else {
                        displayName = "Unknown";
                        displayImage = null;
                    }
                } else { // TEAM context
                    TeamProfile profile = teamProfileRepository
                            .findByTeamIdAndUserId(chat.getTeam().getId(), otherUserId)
                            .orElse(null);
                    if (profile != null) {
                        displayName = profile.getDisplayName();
                        displayImage = profile.getProfileImage();
                        otherParticipant = ParticipantProfile.builder()
                                .userId(otherUserId)
                                .displayName(profile.getDisplayName())
                                .profileImage(profile.getProfileImage())
                                .build();
                    } else {
                        displayName = "Unknown";
                        displayImage = null;
                    }
                }
            } else {
                displayName = "Unknown";
                displayImage = null;
            }
        } else {
            // 그룹 채팅: 채팅방 자체 정보
            displayName = chat.getName();
            displayImage = chat.getProfileImage();
        }

        // 참여자 프로필 목록 생성
        List<ParticipantProfile> participants = chat.getParticipants().stream()
                .map(p -> buildParticipantProfile(p, chat))
                .collect(Collectors.toList());

        Long messageCount = messageRepository.countByChat_IdAndIsDeletedFalse(chat.getId());

        return ChatResponse.builder()
                .chatId(chat.getId())
                .type(chat.getType())
                .context(chat.getContext())
                .displayName(displayName)
                .displayImage(displayImage)
                .name(chat.getName())
                .profileImage(chat.getProfileImage())
                .teamId(chat.getTeam() != null ? chat.getTeam().getId() : null)
                .teamName(chat.getTeam() != null ? chat.getTeam().getName() : null)
                .participantCount((long) chat.getParticipants().size())
                .participants(participants)
                .otherParticipant(otherParticipant)
                .readCount(chat.getReadCount())
                .readEnabled(chat.getReadEnabled())
                .messageCount(messageCount)
                .lastMessageAt(chat.getLastMessageAt())
                .createdAt(chat.getCreatedAt())
                .updatedAt(chat.getUpdatedAt())
                .build();
    }

    private ParticipantProfile buildParticipantProfile(ChatParticipant participant, Chat chat) {
        Long userId = participant.getUser().getId();

        if (chat.getContext() == Chat.ChatContext.FRIEND) {
            AgoraUserProfile profile = agoraUserProfileRepository.findById(userId).orElse(null);
            if (profile != null) {
                return ParticipantProfile.builder()
                        .userId(userId)
                        .displayName(profile.getDisplayName())
                        .profileImage(profile.getProfileImage())
                        .identifier(profile.getAgoraId())
                        .build();
            }
        } else { // TEAM context
            TeamProfile profile = teamProfileRepository
                    .findByTeamIdAndUserId(chat.getTeam().getId(), userId)
                    .orElse(null);
            if (profile != null) {
                return ParticipantProfile.builder()
                        .userId(userId)
                        .displayName(profile.getDisplayName())
                        .profileImage(profile.getProfileImage())
                        .build();
            }
        }

        return ParticipantProfile.builder()
                .userId(userId)
                .displayName("Unknown")
                .build();
    }
}
