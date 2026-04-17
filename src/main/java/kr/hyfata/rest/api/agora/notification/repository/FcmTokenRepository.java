package kr.hyfata.rest.api.agora.notification.repository;

import kr.hyfata.rest.api.entity.agora.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findByUser_Id(Long userId);

    Optional<FcmToken> findByToken(String token);

    Optional<FcmToken> findByUser_IdAndDeviceType(Long userId, FcmToken.DeviceType deviceType);

    boolean existsByToken(String token);

    void deleteByToken(String token);

    void deleteByUser_Id(Long userId);

    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.updatedAt < :dateTime")
    int deleteExpiredTokens(@Param("dateTime") LocalDateTime dateTime);

    @Query("SELECT f.token FROM FcmToken f WHERE f.user.id = :userId")
    List<String> findTokensByUserId(@Param("userId") Long userId);
}
