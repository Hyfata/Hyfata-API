package kr.hyfata.rest.api.agora.chat.service;

import kr.hyfata.rest.api.auth.dto.agora.chat.GroupChatResponse;
import kr.hyfata.rest.api.auth.dto.agora.chat.CreateGroupChatRequest;
import kr.hyfata.rest.api.auth.dto.agora.chat.InviteMembersRequest;

public interface AgoraGroupChatService {

    GroupChatResponse createGroupChat(String userEmail, CreateGroupChatRequest request);

    GroupChatResponse getGroupChatDetail(String userEmail, Long chatId);

    GroupChatResponse updateGroupChat(String userEmail, Long chatId, String name, String profileImage);

    GroupChatResponse inviteMembers(String userEmail, Long chatId, InviteMembersRequest request);

    String removeMember(String userEmail, Long chatId, Long memberUserId);

    String leaveGroup(String userEmail, Long chatId);
}
