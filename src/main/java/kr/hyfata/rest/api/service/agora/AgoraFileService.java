package kr.hyfata.rest.api.service.agora;

import kr.hyfata.rest.api.dto.agora.FileUploadResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AgoraFileService {

    /**
     * 파일 업로드
     * @param userEmail 사용자 이메일
     * @param file 업로드할 파일
     * @return 업로드된 파일 정보
     */
    FileUploadResponse uploadFile(String userEmail, MultipartFile file);

    /**
     * 이미지 업로드 (썸네일 생성)
     * @param userEmail 사용자 이메일
     * @param file 업로드할 이미지
     * @return 업로드된 파일 정보
     */
    FileUploadResponse uploadImage(String userEmail, MultipartFile file);

    /**
     * 파일 메타데이터 조회
     * @param fileId 파일 ID
     * @return 파일 정보
     */
    FileUploadResponse getFileMetadata(Long fileId);

    /**
     * 파일 삭제
     * @param userEmail 사용자 이메일
     * @param fileId 파일 ID
     * @return 삭제 성공 메시지
     */
    String deleteFile(String userEmail, Long fileId);

    /**
     * 파일명으로 파일 리소스 로드
     * @param fileName 파일명
     * @return 파일 리소스
     */
    Resource loadFileAsResource(String fileName);
}
