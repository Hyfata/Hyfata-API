package kr.hyfata.rest.api.controller.agora;

import kr.hyfata.rest.api.dto.agora.chat.ChatResponse;
import kr.hyfata.rest.api.dto.agora.chat.ChatListResponse;
import kr.hyfata.rest.api.dto.agora.chat.CreateChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.CreateDirectChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.CreateGroupChatRequest;
import kr.hyfata.rest.api.dto.agora.chat.MessageDto;
import kr.hyfata.rest.api.dto.agora.chat.SendMessageRequest;
import kr.hyfata.rest.api.entity.agora.Chat;
import kr.hyfata.rest.api.service.agora.AgoraChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agora/chats")
@RequiredArgsConstructor
@Slf4j
public class AgoraChatController {

    private final AgoraChatService agoraChatService;

    /**
     * 채팅방 목록 조회
     * GET /api/agora/chats
     */
    @GetMapping
    public ResponseEntity<List<ChatListResponse>> getChatList(Authentication authentication) {
        String userEmail = authentication.getName();
        List<ChatListResponse> chats = agoraChatService.getChatList(userEmail);
        return ResponseEntity.ok(chats);
    }

    /**
     * 채팅방 생성 또는 기존 채팅방 반환
     * POST /api/agora/chats
     */
    @PostMapping
    public ResponseEntity<ChatResponse> createChat(
            Authentication authentication,
            @Valid @RequestBody CreateChatRequest request
    ) {
        String userEmail = authentication.getName();
        ChatResponse chat = agoraChatService.createChat(userEmail, request);
        return ResponseEntity.ok(chat);
    }

    /**
     * 채팅방 상세 조회
     * GET /api/agora/chats/{chatId}
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<ChatResponse> getChatDetail(
            Authentication authentication,
            @PathVariable Long chatId
    ) {
        String userEmail = authentication.getName();
        ChatResponse chat = agoraChatService.getChatDetail(userEmail, chatId);
        return ResponseEntity.ok(chat);
    }

    /**
     * 메시지 목록 조회 (커서 페이징)
     * GET /api/agora/chats/{chatId}/messages?cursor=&limit=20
     */
    @GetMapping("/{chatId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(
            Authentication authentication,
            @PathVariable Long chatId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String userEmail = authentication.getName();
        List<MessageDto> messages = agoraChatService.getMessages(userEmail, chatId, cursor, limit);
        return ResponseEntity.ok(messages);
    }

    /**
     * 메시지 전송
     * POST /api/agora/chats/{chatId}/messages
     */
    @PostMapping("/{chatId}/messages")
    public ResponseEntity<MessageDto> sendMessage(
            Authentication authentication,
            @PathVariable Long chatId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        String userEmail = authentication.getName();
        MessageDto message = agoraChatService.sendMessage(userEmail, chatId, request);
        return ResponseEntity.ok(message);
    }

    /**
     * 메시지 삭제
     * DELETE /api/agora/chats/{chatId}/messages/{msgId}
     */
    @DeleteMapping("/{chatId}/messages/{msgId}")
    public ResponseEntity<?> deleteMessage(
            Authentication authentication,
            @PathVariable Long chatId,
            @PathVariable Long msgId
    ) {
        String userEmail = authentication.getName();
        String message = agoraChatService.deleteMessage(userEmail, chatId, msgId);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * 채팅방 읽음 처리
     * PUT /api/agora/chats/{chatId}/read
     */
    @PutMapping("/{chatId}/read")
    public ResponseEntity<?> markChatAsRead(
            Authentication authentication,
            @PathVariable Long chatId
    ) {
        String userEmail = authentication.getName();
        String message = agoraChatService.markChatAsRead(userEmail, chatId);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    // ==================== 컨텍스트 기반 새 엔드포인트 ====================

    /**
     * 1:1 채팅 생성/조회
     * POST /api/agora/chats/direct
     */
    @PostMapping("/direct")
    public ResponseEntity<ChatResponse> getOrCreateDirectChat(
            Authentication authentication,
            @Valid @RequestBody CreateDirectChatRequest request
    ) {
        String userEmail = authentication.getName();
        ChatResponse chat = agoraChatService.getOrCreateDirectChat(userEmail, request);
        return ResponseEntity.ok(chat);
    }

    /**
     * 1:1 채팅 목록 조회 (컨텍스트별)
     * GET /api/agora/chats/direct?context=FRIEND|TEAM
     */
    @GetMapping("/direct")
    public ResponseEntity<List<ChatResponse>> getDirectChats(
            Authentication authentication,
            @RequestParam Chat.ChatContext context
    ) {
        String userEmail = authentication.getName();
        List<ChatResponse> chats = agoraChatService.getDirectChatsByContext(userEmail, context);
        return ResponseEntity.ok(chats);
    }

    /**
     * 그룹 채팅 생성 (친구 그룹 채팅)
     * POST /api/agora/chats/group
     */
    @PostMapping("/group")
    public ResponseEntity<ChatResponse> createGroupChat(
            Authentication authentication,
            @Valid @RequestBody CreateGroupChatRequest request
    ) {
        String userEmail = authentication.getName();
        ChatResponse chat = agoraChatService.createGroupChat(userEmail, request);
        return ResponseEntity.ok(chat);
    }

    /**
     * 그룹 채팅 목록 조회 (친구 그룹 채팅)
     * GET /api/agora/chats/group
     */
    @GetMapping("/group")
    public ResponseEntity<List<ChatResponse>> getGroupChats(Authentication authentication) {
        String userEmail = authentication.getName();
        List<ChatResponse> chats = agoraChatService.getGroupChats(userEmail);
        return ResponseEntity.ok(chats);
    }
}
