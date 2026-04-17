package kr.hyfata.rest.api.agora.friend.repository;

import kr.hyfata.rest.api.agora.friend.entity.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    List<BlockedUser> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<BlockedUser> findByUser_IdAndBlockedUser_Id(Long userId, Long blockedUserId);

    boolean existsByUser_IdAndBlockedUser_Id(Long userId, Long blockedUserId);

    void deleteByUser_IdAndBlockedUser_Id(Long userId, Long blockedUserId);
}
