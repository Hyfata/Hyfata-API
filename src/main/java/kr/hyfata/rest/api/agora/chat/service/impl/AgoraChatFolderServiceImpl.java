package kr.hyfata.rest.api.agora.chat.service.impl;

import kr.hyfata.rest.api.auth.dto.agora.ChatFolderResponse;
import kr.hyfata.rest.api.auth.dto.agora.CreateChatFolderRequest;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.entity.agora.ChatFolder;
import kr.hyfata.rest.api.entity.agora.ChatFolderItem;
import kr.hyfata.rest.api.entity.agora.Chat;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.ChatFolderRepository;
import kr.hyfata.rest.api.repository.agora.ChatFolderItemRepository;
import kr.hyfata.rest.api.repository.agora.ChatRepository;
import kr.hyfata.rest.api.repository.agora.ChatParticipantRepository;
import kr.hyfata.rest.api.agora.chat.service.AgoraChatFolderService;
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
public class AgoraChatFolderServiceImpl implements AgoraChatFolderService {

    private final UserRepository userRepository;
    private final ChatFolderRepository chatFolderRepository;
    private final ChatFolderItemRepository chatFolderItemRepository;
    private final ChatRepository chatRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    @Override
    public List<ChatFolderResponse> getChatFolders(String userEmail) {
        User user = findUserByEmail(userEmail);

        List<ChatFolder> folders = chatFolderRepository.findByUser_IdOrderByOrderIndexAsc(user.getId());

        return folders.stream()
                .map(folder -> {
                    List<Long> chatIds = folder.getItems().stream()
                            .map(item -> item.getChat().getId())
                            .collect(Collectors.toList());
                    return ChatFolderResponse.from(folder, chatIds);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatFolderResponse createChatFolder(String userEmail, CreateChatFolderRequest request) {
        User user = findUserByEmail(userEmail);

        Integer orderIndex = request.getOrderIndex() != null ? request.getOrderIndex() : 0;

        ChatFolder folder = ChatFolder.builder()
                .user(user)
                .name(request.getName())
                .orderIndex(orderIndex)
                .build();

        ChatFolder saved = chatFolderRepository.save(folder);
        return ChatFolderResponse.from(saved, List.of());
    }

    @Override
    @Transactional
    public ChatFolderResponse updateChatFolder(String userEmail, Long folderId, String name, String color) {
        User user = findUserByEmail(userEmail);

        ChatFolder folder = chatFolderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Chat folder not found"));

        // Verify ownership
        if (!folder.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to update this folder");
        }

        if (name != null && !name.isEmpty()) {
            folder.setName(name);
        }
        if (color != null) {
            // color parameter is ignored (ChatFolder doesn't have color field)
            // but kept for API compatibility
        }

        ChatFolder updated = chatFolderRepository.save(folder);

        List<Long> chatIds = updated.getItems().stream()
                .map(item -> item.getChat().getId())
                .collect(Collectors.toList());

        return ChatFolderResponse.from(updated, chatIds);
    }

    @Override
    @Transactional
    public String deleteChatFolder(String userEmail, Long folderId) {
        User user = findUserByEmail(userEmail);

        ChatFolder folder = chatFolderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Chat folder not found"));

        // Verify ownership
        if (!folder.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to delete this folder");
        }

        // Remove all folder items
        chatFolderItemRepository.deleteByFolder_Id(folderId);

        // Delete folder
        chatFolderRepository.deleteById(folderId);

        return "Chat folder deleted";
    }

    @Override
    @Transactional
    public ChatFolderResponse addChatToFolder(String userEmail, Long chatId, Long folderId) {
        User user = findUserByEmail(userEmail);

        ChatFolder folder = chatFolderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Chat folder not found"));

        // Verify folder ownership
        if (!folder.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to modify this folder");
        }

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        // Verify user is participant of chat
        if (!chatParticipantRepository.existsByChat_IdAndUser_Id(chatId, user.getId())) {
            throw new IllegalStateException("You are not a participant of this chat");
        }

        // Check if already in folder
        if (chatFolderItemRepository.existsByFolder_IdAndChat_Id(folderId, chatId)) {
            throw new IllegalStateException("Chat is already in this folder");
        }

        ChatFolderItem item = ChatFolderItem.builder()
                .folder(folder)
                .chat(chat)
                .build();

        chatFolderItemRepository.save(item);

        // Refresh folder
        ChatFolder refreshed = chatFolderRepository.findById(folderId)
                .orElse(folder);

        List<Long> chatIds = refreshed.getItems().stream()
                .map(folderItem -> folderItem.getChat().getId())
                .collect(Collectors.toList());

        return ChatFolderResponse.from(refreshed, chatIds);
    }

    @Override
    @Transactional
    public String removeChatFromFolder(String userEmail, Long chatId) {
        User user = findUserByEmail(userEmail);

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        java.util.List<ChatFolderItem> items = chatFolderItemRepository.findByChat_Id(chatId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Chat is not in any folder");
        }

        ChatFolderItem item = items.get(0);

        // Verify folder ownership
        if (!item.getFolder().getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("You don't have permission to remove this chat from folder");
        }

        chatFolderItemRepository.deleteById(item.getId());
        return "Chat removed from folder";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
