package kr.hyfata.rest.api.client.entity;

import jakarta.persistence.*;
import kr.hyfata.rest.api.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * OAuth 클라이언트 메타데이터 엔티티
 * <p>
 * 프로토콜 정보(시크릿, redirect URI, scope, TokenSettings 등)는 SAS 표준
 * oauth2_registered_client 테이블(JdbcRegisteredClientRepository)이 관리하고,
 * 이 테이블은 등록 API에서 사용하는 정보성 메타데이터만 보관한다.
 */
@Entity
@Table(name = "client_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientMetadata {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    // 소유자 연관관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User owner;

    @Column(length = 500)
    private String description;

    @Column(length = 255)
    private String frontendUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClientType clientType = ClientType.THIRD_PARTY;

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
