package kr.hyfata.rest.api.agora.chat.service.impl;

import kr.hyfata.rest.api.agora.chat.dto.GroupChatResponse;
import kr.hyfata.rest.api.agora.chat.dto.CreateGroupChatRequest;
import kr.hyfata.rest.api.agora.chat.dto.InviteMembersRequest;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.agora.chat.entity.Chat;
import kr.hyfata.rest.api.agora.chat.entity.ChatParticipant;
import kr.hyfata.rest.api.agora.profile.entity.AgoraUserProfile;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.agora.profile.repository.AgoraUserProfileRepository;
import kr.hyfata.rest.api.agora.chat.repository.ChatRepository;
import kr.hyfata.rest.api.agora.chat.repository.ChatParticipantRepository;
import kr.hyfata.rest.api.agora.chat.service.AgoraGroupChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraGroupChatServiceImpl implements AgoraGroupChatService {

    private final UserRepository userRepository;
    private final AgoraUserProfileRepository agoraUserProfileRepository;
    private final ChatRepository chatRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    @Override
    @Transactional
    public GroupChatResponse createGroupChat(String userEmail, CreateGroupChatRequest request) {
        User creator = findUserByEmail(userEmail);

        // Create group chat
        Chat chat = Chat.builder()
                .type(Chat.ChatType.GROUP)
                .name(request.getName())
                .profileImage(request.getProfileImage())
                .createdBy(creator)
                .readEnabled(true)
                .build();

        Chat savedChat = chatRepository.save(chat);

        // Add creator as ADMIN
        ChatParticipant adminParticipant = ChatParticipant.builder()
                .chat(savedChat)
                .user(creator)
                .role(ChatParticipant.Role.ADMIN)
                .build();
        chatParticipantRepository.save(adminParticipant);

        // Add members
        for (String memberAgoraId : request.getMemberAgoraIds()) {
            AgoraUserProfile memberProfile = agoraUserProfileRepository.findByAgoraId(memberAgoraId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with agoraId: " + memberAgoraId));

            User member = memberProfile.getUser();

            ChatParticipant memberParticipant = ChatParticipant.builder()
                    .chat(savedChat)
                    .user(member)
                    .role(ChatParticipant.Role.MEMBER)
                    .build();
            chatParticipantRepository.save(memberParticipant);
        }

        List<String> memberAgoraIds = getMemberAgoraIds(savedChat);
        AgoraUserProfile creatorProfile = agoraUserProfileRepository.findById(creator.getId()).orElse(null);
        String creatorAgoraId = creatorProfile != null ? creatorProfile.getAgoraId() : "";

        return GroupChatResponse.from(savedChat, memberAgoraIds, creatorAgoraId);
    }

    @Override
    public GroupChatResponse getGroupChatDetail(String userEmail, Long chatId) {
        User user = findUserByEmail(userEmail);

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        // Verify chat is GROUP type
        if (!Chat.ChatType.GROUP.equals(chat.getType())) {
            throw new IllegalArgumentException("This is not a group chat");
        }

        // Verify user is participant
        if (!chatParticipantRepository.existsByChat_IdAndUser_Id(chatId, user.getId())) {
            throw new IllegalStateException("User is not a participant of this chat");
        }

        List<String> memberAgoraIds = getMemberAgoraIds(chat);
        AgoraUserProfile creatorProfile = agoraUserProfileRepository.findById(chat.getCreatedBy().getId()).orElse(null);
        String creatorAgoraId = creatorProfile != null ? creatorProfile.getAgoraId() : "";

        return GroupChatResponse.from(chat, memberAgoraIds, creatorAgoraId);
    }

    @Override
    @Transactional
    public GroupChatResponse updateGroupChat(String userEmail, Long chatId, String name, String profileImage) {
        User user = findUserByEmail(userEmail);

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        // Verify user is ADMIN
        ChatParticipant participant = chatParticipantRepository.findByChat_IdAndUser_Id(chatId, user.getId())
                .orElseThrow(() -> new IllegalStateException("User is not a participant of this chat"));

        if (!ChatParticipant.Role.ADMIN.equals(participant.getRole())) {
            throw new IllegalStateException("Only admin can update group chat");
        }

        if (name != null && !name.isEmpty()) {
            chat.setName(name);
        }
        if (profileImage != null) {
            chat.setProfileImage(profileImage);
        }

        Chat updated = chatRepository.save(chat);

        List<String> memberAgoraIds = getMemberAgoraIds(updated);
        AgoraUserProfile creatorProfile = agoraUserProfileRepository.findById(updated.getCreatedBy().getId()).orElse(null);
        String creatorAgoraId = creatorProfile != null ? creatorProfile.getAgoraId() : "";

        return GroupChatResponse.from(updated, memberAgoraIds, creatorAgoraId);
    }

    @Override
    @Transactional
    public GroupChatResponse inviteMembers(String userEmail, Long chatId, InviteMembersRequest request) {
        User user = findUserByEmail(userEmail);

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        // Verify user is ADMIN
        ChatParticipant participant = chatParticipantRepository.findByChat_IdAndUser_Id(chatId, user.getId())
                .orElseThrow(() -> new IllegalStateException("User is not a participant of this chat"));

        if (!ChatParticipant.Role.ADMIN.equals(participant.getRole())) {
            throw new IllegalStateException("Only admin can invite members");
        }

        // Add new members
        for (String memberAgoraId : request.getMemberAgoraIds()) {
            AgoraUserProfile memberProfile = agoraUserProfileRepository.findByAgoraId(memberAgoraId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with agoraId: " + memberAgoraId));

            User member = memberProfile.getUser();

            // Check if already a member
            if (!chatParticipantRepository.existsByChat_IdAndUser_Id(chatId, member.getId())) {
                ChatParticipant newMember = ChatParticipant.builder()
                        .chat(chat)
                        .user(member)
                        .role(ChatParticipant.Role.MEMBER)
                        .build();
                chatParticipantRepository.save(newMember);
            }
        }

        List<String> memberAgoraIds = getMemberAgoraIds(chat);
        AgoraUserProfile creatorProfile = agoraUserProfileRepository.findById(chat.getCreatedBy().getId()).orElse(null);
        String creatorAgoraId = creatorProfile != null ? creatorProfile.getAgoraId() : "";

        return GroupChatResponse.from(chat, memberAgoraIds, creatorAgoraId);
    }

    @Override
    @Transactional
    public String removeMember(String userEmail, Long chatId, Long memberUserId) {
        User user = findUserByEmail(userEmail);

        chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        // Verify user is ADMIN
        ChatParticipant adminParticipant = chatParticipantRepository.findByChat_IdAndUser_Id(chatId, user.getId())
                .orElseThrow(() -> new IllegalStateException("User is not a participant of this chat"));

        if (!ChatParticipant.Role.ADMIN.equals(adminParticipant.getRole())) {
            throw new IllegalStateException("Only admin can remove members");
        }

        // Cannot remove admin
        if (user.getId().equals(memberUserId)) {
            throw new IllegalStateException("Admin cannot remove themselves. Leave the group instead");
        }

        chatParticipantRepository.deleteByChat_IdAndUser_Id(chatId, memberUserId);
        return "Member removed";
    }

    @Override
    @Transactional
    public String leaveGroup(String userEmail, Long chatId) {
        User user = findUserByEmail(userEmail);

        chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        ChatParticipant participant = chatParticipantRepository.findByChat_IdAndUser_Id(chatId, user.getId())
                .orElseThrow(() -> new IllegalStateException("User is not a participant of this chat"));

        // If user is admin and only one admin, transfer to another admin if exists
        if (ChatParticipant.Role.ADMIN.equals(participant.getRole())) {
            List<ChatParticipant> admins = chatParticipantRepository.findByChat_IdAndRole(chatId, ChatParticipant.Role.ADMIN);
            if (admins.size() == 1) {
                List<ChatParticipant> members = chatParticipantRepository.findByChat_IdAndRole(chatId, ChatParticipant.Role.MEMBER);
                if (!members.isEmpty()) {
                    members.get(0).setRole(ChatParticipant.Role.ADMIN);
                    chatParticipantRepository.save(members.get(0));
                }
            }
        }

        chatParticipantRepository.deleteByChat_IdAndUser_Id(chatId, user.getId());
        return "Left group";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private List<String> getMemberAgoraIds(Chat chat) {
        return chat.getParticipants().stream()
                .map(participant -> {
                    AgoraUserProfile profile = agoraUserProfileRepository.findById(participant.getUser().getId())
                            .orElse(null);
                    return profile != null ? profile.getAgoraId() : "";
                })
                .collect(Collectors.toList());
    }
}
