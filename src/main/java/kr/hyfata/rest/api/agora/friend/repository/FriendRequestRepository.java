package kr.hyfata.rest.api.agora.friend.repository;

import kr.hyfata.rest.api.agora.friend.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    List<FriendRequest> findByToUser_IdAndStatusOrderByCreatedAtDesc(Long toUserId, FriendRequest.Status status);

    List<FriendRequest> findByFromUser_IdOrderByCreatedAtDesc(Long fromUserId);

    Optional<FriendRequest> findByFromUser_IdAndToUser_Id(Long fromUserId, Long toUserId);

    boolean existsByFromUser_IdAndToUser_IdAndStatus(Long fromUserId, Long toUserId, FriendRequest.Status status);

    long countByToUser_IdAndStatus(Long toUserId, FriendRequest.Status status);
}
