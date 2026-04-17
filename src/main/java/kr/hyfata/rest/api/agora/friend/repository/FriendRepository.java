package kr.hyfata.rest.api.agora.friend.repository;

import kr.hyfata.rest.api.agora.friend.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRepository extends JpaRepository<Friend, Long> {

    List<Friend> findByUser_IdOrderByIsFavoriteDescCreatedAtDesc(Long userId);

    List<Friend> findByUser_IdAndIsFavoriteTrue(Long userId);

    Optional<Friend> findByUser_IdAndFriend_Id(Long userId, Long friendId);

    boolean existsByUser_IdAndFriend_Id(Long userId, Long friendId);

    void deleteByUser_IdAndFriend_Id(Long userId, Long friendId);

    long countByUser_Id(Long userId);
}
