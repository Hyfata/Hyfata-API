package kr.hyfata.rest.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OAuth 2.0 Authorization Code
 * 사용자가 로그인 후 발급되며, 클라이언트가 이를 accessToken으로 교환
 */
@Entity
@Table(name = "authorization_codes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorizationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 255)
    private String code;

    @Column(nullable = false, length = 100)
    private String clientId;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 255)
    private String redirectUri;

    @Column(length = 255)
    private String state;  // CSRF 방지용

    @Column(length = 500)
    private String codeChallenge;  // PKCE code challenge (SHA-256 해시 후 Base64URL 인코딩)

    @Column(length = 50)
    private String codeChallengeMethod;  // PKCE method (S256 or plain)

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;  // 한 번만 사용 가능

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);  // 10분 유효

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
