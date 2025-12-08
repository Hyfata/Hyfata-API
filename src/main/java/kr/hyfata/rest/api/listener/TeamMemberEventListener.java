package kr.hyfata.rest.api.listener;

import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.agora.Chat;
import kr.hyfata.rest.api.entity.agora.ChatParticipant;
import kr.hyfata.rest.api.event.TeamMemberEvent;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.ChatParticipantRepository;
import kr.hyfata.rest.api.repository.agora.ChatRepository;
import kr.hyfata.rest.api.service.agora.AgoraChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TeamMemberEventListener {

    private final ChatRepository chatRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final UserRepository userRepository;
    private final AgoraChatService agoraChatService;

    @EventListener
    @Transactional
    public void handleTeamCreated(TeamMemberEvent.TeamCreatedEvent event) {
        log.info("Team created event received: teamId={}, creatorUserId={}",
                event.getTeamId(), event.getCreatorUserId());

        try {
            agoraChatService.createTeamGroupChat(event.getTeamId(), event.getCreatorUserId());
            log.info("Team group chat created for team: {}", event.getTeamId());
        } catch (Exception e) {
            log.error("Failed to create team group chat for team: {}", event.getTeamId(), e);
        }
    }

    @EventListener
    @Transactional
    public void handleTeamMemberAdded(TeamMemberEvent.TeamMemberAddedEvent event) {
        log.info("Team member added event received: teamId={}, userId={}",
                event.getTeamId(), event.getUserId());

        // 팀 그룹 채팅 찾기
        Optional<Chat> teamChatOpt = chatRepository.findByTeam_IdAndTypeAndContext(
                event.getTeamId(), Chat.ChatType.GROUP, Chat.ChatContext.TEAM);

        if (teamChatOpt.isEmpty()) {
            log.warn("Team group chat not found for team: {}", event.getTeamId());
            return;
        }

        Chat teamChat = teamChatOpt.get();

        // 이미 참여자인지 확인
        if (chatParticipantRepository.existsByChat_IdAndUser_Id(teamChat.getId(), event.getUserId())) {
            log.info("User {} is already a participant of team chat {}", event.getUserId(), teamChat.getId());
            return;
        }

        // 사용자 찾기
        User user = userRepository.findById(event.getUserId()).orElse(null);
        if (user == null) {
            log.error("User not found: {}", event.getUserId());
            return;
        }

        // 참여자 추가
        ChatParticipant participant = ChatParticipant.builder()
                .chat(teamChat)
                .user(user)
                .role(ChatParticipant.Role.MEMBER)
                .build();

        chatParticipantRepository.save(participant);
        log.info("Added user {} to team chat {}", event.getUserId(), teamChat.getId());
    }

    @EventListener
    @Transactional
    public void handleTeamMemberRemoved(TeamMemberEvent.TeamMemberRemovedEvent event) {
        log.info("Team member removed event received: teamId={}, userId={}",
                event.getTeamId(), event.getUserId());

        // 팀 그룹 채팅 찾기
        Optional<Chat> teamChatOpt = chatRepository.findByTeam_IdAndTypeAndContext(
                event.getTeamId(), Chat.ChatType.GROUP, Chat.ChatContext.TEAM);

        if (teamChatOpt.isEmpty()) {
            log.warn("Team group chat not found for team: {}", event.getTeamId());
            return;
        }

        Chat teamChat = teamChatOpt.get();

        // 참여자 제거
        chatParticipantRepository.deleteByChat_IdAndUser_Id(teamChat.getId(), event.getUserId());
        log.info("Removed user {} from team chat {}", event.getUserId(), teamChat.getId());
    }
}
