package kr.hyfata.rest.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OAuth 클라이언트 엔티티
 * 여러 웹사이트/애플리케이션이 이 API를 통해 인증을 제공받도록 함
 */
@Entity
@Table(name = "clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String clientId;

    @Column(nullable = false, length = 255)
    private String clientSecret;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 255)
    private String frontendUrl;

    @Column(nullable = false)
    private String redirectUris;  // JSON 형식 또는 쉼표로 구분된 URI들

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    // 접근 통제
    @Column(nullable = false)
    @Builder.Default
    private Integer maxTokensPerUser = 5;  // 사용자당 최대 토큰 수

    // 메타데이터
    @Column(length = 255)
    private String ownerEmail;  // 클라이언트 소유자

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
