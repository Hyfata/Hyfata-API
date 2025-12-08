package kr.hyfata.rest.api.service.agora;

import kr.hyfata.rest.api.dto.agora.chat.ChatResponse;
import kr.hyfata.rest.api.dto.agora.chat.ChatListResponse;
import kr.hyfata.rest.api.dto.agora.chat.CreateChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.CreateDirectChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.CreateGroupChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.MessageDto;
import kr.hyfata.rest.api.dto.agora.chat.SendMessageRequest;
import kr.hyfata.rest.api.entity.agora.Chat;

import java.util.List;

public interface AgoraChatService {

    // 기존 메서드
    List<ChatListResponse> getChatList(String userEmail);

    ChatResponse createChat(String userEmail, CreateChatRequest request);

    ChatResponse getChatDetail(String userEmail, Long chatId);

    List<MessageDto> getMessages(String userEmail, Long chatId, Long cursor, int limit);

    MessageDto sendMessage(String userEmail, Long chatId, SendMessageRequest request);

    String deleteMessage(String userEmail, Long chatId, Long messageId);

    String markChatAsRead(String userEmail, Long chatId);

    // 새로운 컨텍스트 기반 메서드
    ChatResponse getOrCreateDirectChat(String userEmail, CreateDirectChatRequest request);

    ChatResponse createGroupChat(String userEmail, CreateGroupChatRequest request);

    ChatResponse createTeamGroupChat(Long teamId, Long creatorUserId);

    List<ChatResponse> getDirectChatsByContext(String userEmail, Chat.ChatContext context);

    List<ChatResponse> getGroupChats(String userEmail);

    ChatResponse getTeamGroupChat(Long teamId, String userEmail);

    ChatResponse getChatDetailWithProfiles(String userEmail, Long chatId);
}
