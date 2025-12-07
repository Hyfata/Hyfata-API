package kr.hyfata.rest.api.service.agora.impl;

import kr.hyfata.rest.api.config.FileStorageConfig;
import kr.hyfata.rest.api.dto.agora.FileUploadResponse;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.agora.AgoraFile;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.AgoraFileRepository;
import kr.hyfata.rest.api.service.agora.AgoraFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnailator;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AgoraFileServiceImpl implements AgoraFileService {

    private final UserRepository userRepository;
    private final AgoraFileRepository agoraFileRepository;

    @Override
    public FileUploadResponse uploadFile(String userEmail, MultipartFile file) {
        User user = findUser(userEmail);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다");
        }

        String mimeType = file.getContentType();
        if (!FileStorageConfig.isAllowedMimeType(mimeType)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + mimeType);
        }

        if (!FileStorageConfig.isValidFileSize(file.getSize(), mimeType)) {
            throw new IllegalArgumentException("파일 크기가 너무 큽니다");
        }

        AgoraFile.FileType fileType = determineFileType(mimeType);
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path uploadPath = FileStorageConfig.getUploadPath().resolve(fileName);

        try {
            Files.write(uploadPath, file.getBytes());

            AgoraFile agoraFile = AgoraFile.builder()
                    .uploadedBy(user)
                    .fileName(fileName)
                    .originalName(file.getOriginalFilename())
                    .filePath(uploadPath.toString())
                    .fileUrl("/api/agora/files/" + fileName)
                    .fileSize(file.getSize())
                    .mimeType(mimeType)
                    .fileType(fileType)
                    .build();

            AgoraFile saved = agoraFileRepository.save(agoraFile);
            return FileUploadResponse.from(saved);

        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public FileUploadResponse uploadImage(String userEmail, MultipartFile file) {
        FileUploadResponse response = uploadFile(userEmail, file);

        // 썸네일 생성
        try {
            String fileName = response.getFileName();
            Path originalPath = FileStorageConfig.getUploadPath().resolve(fileName);
            String thumbnailName = "thumb_" + fileName;
            Path thumbnailPath = FileStorageConfig.getUploadPath().resolve(thumbnailName);

            Thumbnailator.createThumbnail(originalPath.toFile(), thumbnailPath.toFile(), 200, 200);

            // 데이터베이스 업데이트
            AgoraFile file_entity = agoraFileRepository.findById(response.getFileId())
                    .orElseThrow(() -> new IllegalStateException("파일을 찾을 수 없습니다"));
            file_entity.setThumbnailUrl("/api/agora/files/" + thumbnailName);
            agoraFileRepository.save(file_entity);

            response.setThumbnailUrl("/api/agora/files/" + thumbnailName);
        } catch (IOException e) {
            log.warn("썸네일 생성 실패: " + e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public FileUploadResponse getFileMetadata(Long fileId) {
        return agoraFileRepository.findById(fileId)
                .map(FileUploadResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다"));
    }

    @Override
    public String deleteFile(String userEmail, Long fileId) {
        User user = findUser(userEmail);

        AgoraFile file = agoraFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다"));

        if (!file.getUploadedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("본인이 업로드한 파일만 삭제할 수 있습니다");
        }

        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
            if (file.getThumbnailUrl() != null) {
                String thumbnailName = file.getThumbnailUrl().substring(file.getThumbnailUrl().lastIndexOf("/") + 1);
                Files.deleteIfExists(FileStorageConfig.getUploadPath().resolve(thumbnailName));
            }
        } catch (IOException e) {
            log.warn("파일 삭제 실패: " + e.getMessage());
        }

        agoraFileRepository.deleteById(fileId);
        return "파일이 삭제되었습니다";
    }

    private User findUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private AgoraFile.FileType determineFileType(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return AgoraFile.FileType.IMAGE;
        } else if (mimeType.startsWith("video/")) {
            return AgoraFile.FileType.VIDEO;
        } else if (mimeType.contains("pdf") || mimeType.contains("document")) {
            return AgoraFile.FileType.DOCUMENT;
        }
        return AgoraFile.FileType.OTHER;
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = FileStorageConfig.getUploadPath().resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName, e);
        }
    }
}
