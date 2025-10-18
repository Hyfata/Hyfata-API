package kr.hyfata.rest.api.repository;

import kr.hyfata.rest.api.entity.AuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AuthorizationCodeRepository extends JpaRepository<AuthorizationCode, Long> {
    Optional<AuthorizationCode> findByCode(String code);
    Optional<AuthorizationCode> findByCodeAndClientId(String code, String clientId);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);  // 만료된 코드 정리
}
