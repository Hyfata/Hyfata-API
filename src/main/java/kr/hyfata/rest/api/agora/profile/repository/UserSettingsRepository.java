package kr.hyfata.rest.api.agora.profile.repository;

import kr.hyfata.rest.api.agora.profile.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
