package kr.hyfata.rest.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByResetPasswordToken(String token);
    Optional<User> findByEmailVerificationToken(String token);
    Optional<User> findByRestoreToken(String token);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}