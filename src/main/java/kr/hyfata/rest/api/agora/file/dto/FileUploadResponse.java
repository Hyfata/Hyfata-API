package kr.hyfata.rest.api.agora.file.dto;

import kr.hyfata.rest.api.agora.file.entity.AgoraFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadResponse {

    private Long fileId;

    private String fileName;

    private String originalName;

    private String fileUrl;

    private String thumbnailUrl;

    private Long fileSize;

    private String mimeType;

    private String fileType;

    private LocalDateTime createdAt;

    public static FileUploadResponse from(AgoraFile file) {
        return FileUploadResponse.builder()
                .fileId(file.getId())
                .fileName(file.getFileName())
                .originalName(file.getOriginalName())
                .fileUrl(file.getFileUrl())
                .thumbnailUrl(file.getThumbnailUrl())
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .fileType(file.getFileType().toString())
                .createdAt(file.getCreatedAt())
                .build();
    }
}
