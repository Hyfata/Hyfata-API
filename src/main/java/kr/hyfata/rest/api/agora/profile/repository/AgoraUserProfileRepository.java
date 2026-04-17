package kr.hyfata.rest.api.agora.profile.repository;

import kr.hyfata.rest.api.entity.agora.AgoraUserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgoraUserProfileRepository extends JpaRepository<AgoraUserProfile, Long> {

    Optional<AgoraUserProfile> findByAgoraId(String agoraId);

    boolean existsByAgoraId(String agoraId);

    List<AgoraUserProfile> findByDisplayNameContaining(String displayName);

    List<AgoraUserProfile> findByAgoraIdContainingOrDisplayNameContaining(String agoraId, String displayName);

    Page<AgoraUserProfile> findByAgoraIdContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String agoraId, String displayName, Pageable pageable);
}
