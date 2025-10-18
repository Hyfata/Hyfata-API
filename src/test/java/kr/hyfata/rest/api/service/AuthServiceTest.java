package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void testUserCanBeCreatedAndRetrieved() {
        User user = User.builder()
                .email("test@example.com")
                .username("testuser")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .twoFactorEnabled(false)
                .emailVerified(false)
                .build();

        userRepository.save(user);

        User retrievedUser = userRepository.findByEmail("test@example.com").orElse(null);
        assertNotNull(retrievedUser);
        assertEquals("testuser", retrievedUser.getUsername());
        assertEquals("Test", retrievedUser.getFirstName());
        assertFalse(retrievedUser.getEmailVerified());
    }

    @Test
    void testPasswordEncodingWorks() {
        String rawPassword = "Password123!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
        assertFalse(passwordEncoder.matches("WrongPassword", encodedPassword));
    }

    @Test
    void testFindUserByUsername() {
        User user = User.builder()
                .email("test@example.com")
                .username("testuser")
                .password(passwordEncoder.encode("Password123!"))
                .enabled(true)
                .build();

        userRepository.save(user);

        User foundUser = userRepository.findByUsername("testuser").orElse(null);
        assertNotNull(foundUser);
        assertEquals("test@example.com", foundUser.getEmail());
    }

    @Test
    void testCheckEmailExists() {
        User user = User.builder()
                .email("test@example.com")
                .username("testuser")
                .password(passwordEncoder.encode("Password123!"))
                .enabled(true)
                .build();

        userRepository.save(user);

        assertTrue(userRepository.existsByEmail("test@example.com"));
        assertFalse(userRepository.existsByEmail("nonexistent@example.com"));
    }

    @Test
    void testUserWithTwoFactorSettings() {
        User user = User.builder()
                .email("test@example.com")
                .username("testuser")
                .password(passwordEncoder.encode("Password123!"))
                .twoFactorEnabled(true)
                .twoFactorCode("123456")
                .enabled(true)
                .build();

        userRepository.save(user);

        User retrievedUser = userRepository.findByEmail("test@example.com").orElse(null);
        assertNotNull(retrievedUser);
        assertTrue(retrievedUser.getTwoFactorEnabled());
        assertEquals("123456", retrievedUser.getTwoFactorCode());
    }
}
