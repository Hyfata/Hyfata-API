package kr.hyfata.rest.api.repository.agora;

import kr.hyfata.rest.api.entity.agora.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findByCreatedBy_Id(Long userId);

    List<Chat> findByType(Chat.ChatType type);

    @Query("SELECT c FROM Chat c JOIN c.participants p WHERE p.user.id = :userId ORDER BY c.updatedAt DESC")
    List<Chat> findChatsByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM Chat c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE c.type = 'DIRECT' AND p1.user.id = :userId1 AND p2.user.id = :userId2")
    Optional<Chat> findDirectChatBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    // 컨텍스트 기반 조회
    List<Chat> findByContext(Chat.ChatContext context);

    List<Chat> findByTypeAndContext(Chat.ChatType type, Chat.ChatContext context);

    // 팀 관련 조회
    List<Chat> findByTeam_Id(Long teamId);

    Optional<Chat> findByTeam_IdAndTypeAndContext(Long teamId, Chat.ChatType type, Chat.ChatContext context);

    boolean existsByTeam_IdAndTypeAndContext(Long teamId, Chat.ChatType type, Chat.ChatContext context);

    // 사용자별 컨텍스트 기반 채팅 목록 (lastMessageAt 기준 정렬)
    @Query("SELECT c FROM Chat c JOIN c.participants p " +
           "WHERE p.user.id = :userId AND c.context = :context " +
           "ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC")
    List<Chat> findChatsByUserIdAndContext(@Param("userId") Long userId, @Param("context") Chat.ChatContext context);

    // 사용자별 타입 + 컨텍스트 기반 채팅 목록
    @Query("SELECT c FROM Chat c JOIN c.participants p " +
           "WHERE p.user.id = :userId AND c.type = :type AND c.context = :context " +
           "ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC")
    List<Chat> findChatsByUserIdAndTypeAndContext(
            @Param("userId") Long userId,
            @Param("type") Chat.ChatType type,
            @Param("context") Chat.ChatContext context);

    // 팀 내 1:1 채팅 조회
    @Query("SELECT c FROM Chat c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE c.type = 'DIRECT' AND c.context = 'TEAM' AND c.team.id = :teamId " +
           "AND p1.user.id = :userId1 AND p2.user.id = :userId2")
    Optional<Chat> findTeamDirectChatBetweenUsers(
            @Param("teamId") Long teamId,
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2);

    // 친구 1:1 채팅 조회
    @Query("SELECT c FROM Chat c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE c.type = 'DIRECT' AND c.context = 'FRIEND' " +
           "AND p1.user.id = :userId1 AND p2.user.id = :userId2")
    Optional<Chat> findFriendDirectChatBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2);
}
