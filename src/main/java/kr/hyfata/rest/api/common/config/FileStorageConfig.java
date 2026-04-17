package kr.hyfata.rest.api.common.config;

import org.springframework.context.annotation.Configuration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

@Configuration
public class FileStorageConfig {

    private static final String UPLOAD_DIR = System.getProperty("user.home") + "/uploads";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    public static final String[] ALLOWED_MIME_TYPES = {
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "video/mp4",
        "video/mpeg",
        "video/quicktime",
        "application/pdf"
    };

    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024; // 100MB
    public static final long MAX_FILE_SIZE_DEFAULT = 50 * 1024 * 1024; // 50MB

    static {
        initializeUploadDirectory();
    }

    private static void initializeUploadDirectory() {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            System.err.println("경고: 파일 저장 디렉토리 생성 실패: " + UPLOAD_DIR + ". " + e.getMessage());
        }
    }

    public static Path getUploadPath() {
        return Paths.get(UPLOAD_DIR);
    }

    public static boolean isAllowedMimeType(String mimeType) {
        for (String allowed : ALLOWED_MIME_TYPES) {
            if (allowed.equals(mimeType)) {
                return true;
            }
        }
        return false;
    }

    public static long getMaxFileSize(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return MAX_IMAGE_SIZE;
        } else if (mimeType.startsWith("video/")) {
            return MAX_VIDEO_SIZE;
        }
        return MAX_FILE_SIZE_DEFAULT;
    }

    public static boolean isValidFileSize(long fileSize, String mimeType) {
        return fileSize <= getMaxFileSize(mimeType);
    }
}
