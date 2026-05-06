package kr.hyfata.rest.api.util;

import kr.hyfata.rest.api.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "hyfata-secret-key-for-jwt-token-min-32-characters-required-for-security");
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 86400000L); // 24 hours
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604800000L); // 7 days

        testUser = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }

    @Test
    void testGenerateAccessToken() {
        String token = jwtUtil.generateAccessToken(testUser);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGenerateRefreshToken() {
        String token = jwtUtil.generateRefreshToken(testUser);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testExtractEmail() {
        String token = jwtUtil.generateAccessToken(testUser);
        String email = jwtUtil.extractEmail(token);
        assertEquals("test@example.com", email);
    }

    @Test
    void testExtractExpiration() {
        String token = jwtUtil.generateAccessToken(testUser);
        Date expiration = jwtUtil.extractExpiration(token);
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void testValidateToken_Valid() {
        String token = jwtUtil.generateAccessToken(testUser);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void testValidateToken_WithUserDetails() {
        String token = jwtUtil.generateAccessToken(testUser);
        assertTrue(jwtUtil.validateToken(token, testUser));
    }

    @Test
    void testValidateToken_InvalidToken() {
        String invalidToken = "invalid.token.here";
        assertFalse(jwtUtil.validateToken(invalidToken));
    }

    @Test
    void testValidateToken_WrongUser() {
        String token = jwtUtil.generateAccessToken(testUser);

        UserDetails differentUser = User.builder()
                .username("different@example.com")
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        assertFalse(jwtUtil.validateToken(token, differentUser));
    }

    @Test
    void testGenerateAccessTokenWithClientIdAndScopes() {
        Set<String> scopes = Set.of("profile", "email", "account:manage");
        JwtUtil.TokenResult result = jwtUtil.generateAccessTokenWithJti(testUser, "client_001", scopes);

        assertNotNull(result);
        assertNotNull(result.token());
        assertNotNull(result.jti());

        String clientId = jwtUtil.extractClientId(result.token());
        Set<String> extractedScopes = jwtUtil.extractScopes(result.token());

        assertEquals("client_001", clientId);
        assertTrue(extractedScopes.contains("profile"));
        assertTrue(extractedScopes.contains("email"));
        assertTrue(extractedScopes.contains("account:manage"));
    }

    @Test
    void testExtractScopes_EmptyToken() {
        String token = jwtUtil.generateAccessToken(testUser);
        Set<String> scopes = jwtUtil.extractScopes(token);

        assertNotNull(scopes);
        assertTrue(scopes.isEmpty());
    }

    @Test
    void testExtractClientId_EmptyToken() {
        String token = jwtUtil.generateAccessToken(testUser);
        String clientId = jwtUtil.extractClientId(token);

        assertNull(clientId);
    }
}
