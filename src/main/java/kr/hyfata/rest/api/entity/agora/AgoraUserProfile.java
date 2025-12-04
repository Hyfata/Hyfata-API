package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import kr.hyfata.rest.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "agora_user_profiles",
        indexes = {
                @Index(name = "idx_agora_profiles_agora_id", columnList = "agora_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgoraUserProfile {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Column(unique = true, nullable = false, length = 50)
    private String agoraId;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String profileImage;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 20)
    private String phone;

    @Column
    private LocalDate birthday;

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
