package kr.hyfata.rest.api.repository.agora;

import kr.hyfata.rest.api.entity.agora.TeamProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamProfileRepository extends JpaRepository<TeamProfile, Long> {

    Optional<TeamProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
