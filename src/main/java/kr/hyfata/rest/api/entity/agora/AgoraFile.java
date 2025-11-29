package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import kr.hyfata.rest.api.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "files",
        indexes = {
                @Index(name = "idx_files_uploaded_by", columnList = "uploaded_by"),
                @Index(name = "idx_files_file_type", columnList = "file_type")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgoraFile {

    public enum FileType {
        IMAGE, VIDEO, DOCUMENT, OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Column(columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileType fileType;

    @OneToOne(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    private FileMetadata metadata;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
