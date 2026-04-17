package kr.hyfata.rest.api.agora.chat.service;

import kr.hyfata.rest.api.auth.dto.agora.ChatFolderResponse;
import kr.hyfata.rest.api.auth.dto.agora.CreateChatFolderRequest;

import java.util.List;

public interface AgoraChatFolderService {

    List<ChatFolderResponse> getChatFolders(String userEmail);

    ChatFolderResponse createChatFolder(String userEmail, CreateChatFolderRequest request);

    ChatFolderResponse updateChatFolder(String userEmail, Long folderId, String name, String color);

    String deleteChatFolder(String userEmail, Long folderId);

    ChatFolderResponse addChatToFolder(String userEmail, Long chatId, Long folderId);

    String removeChatFromFolder(String userEmail, Long chatId);
}
