package kr.hyfata.rest.api.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (Proof Key for Code Exchange) 유틸리티
 * RFC 7636: OAuth 2.0 Public Client를 위한 보안 강화
 *
 * Flutter 같은 모바일 앱에서 Authorization Code Flow를 안전하게 사용할 수 있도록 함
 */
@Component
@Slf4j
public class PkceUtil {

    private static final String VALID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final int CODE_VERIFIER_MIN_LENGTH = 43;
    private static final int CODE_VERIFIER_MAX_LENGTH = 128;
    private final SecureRandom random = new SecureRandom();

    /**
     * Code Verifier 생성
     * - 43-128자 사이의 임의 문자열
     * - 클라이언트에서만 알고 있는 값
     *
     * @return 생성된 code_verifier
     */
    public String generateCodeVerifier() {
        // 128자 길이의 code_verifier 생성 (최대 보안)
        StringBuilder verifier = new StringBuilder();
        for (int i = 0; i < CODE_VERIFIER_MAX_LENGTH; i++) {
            verifier.append(VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length())));
        }
        return verifier.toString();
    }

    /**
     * Code Challenge 생성
     * - code_verifier를 SHA-256으로 해시
     * - Base64URL 인코딩
     *
     * @param codeVerifier code_verifier 값
     * @return 생성된 code_challenge
     * @throws RuntimeException SHA-256 해시 실패 시
     */
    public String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));

            // Base64URL 인코딩 (패딩 제거)
            String base64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            log.debug("Code challenge generated for code verifier");
            return base64Url;
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to generate code challenge", e);
        }
    }

    /**
     * Code Challenge 검증
     * - 제공된 code_verifier를 SHA-256으로 해시
     * - Base64URL 인코딩한 결과가 저장된 code_challenge와 일치하는지 확인
     *
     * @param codeVerifier 클라이언트가 제공한 code_verifier
     * @param storedCodeChallenge 서버에 저장된 code_challenge
     * @return 검증 성공 여부
     */
    public boolean verifyCodeChallenge(String codeVerifier, String storedCodeChallenge) {
        try {
            String generatedChallenge = generateCodeChallenge(codeVerifier);
            boolean isValid = generatedChallenge.equals(storedCodeChallenge);

            if (isValid) {
                log.debug("Code challenge verification successful");
            } else {
                log.warn("Code challenge verification failed");
            }

            return isValid;
        } catch (Exception e) {
            log.error("Code challenge verification error", e);
            return false;
        }
    }

    /**
     * Code Verifier 유효성 검증
     * - 길이 확인 (43-128자)
     * - 허용 문자 확인
     *
     * @param codeVerifier code_verifier 값
     * @return 유효성 검증 결과
     */
    public boolean isValidCodeVerifier(String codeVerifier) {
        if (codeVerifier == null || codeVerifier.isEmpty()) {
            return false;
        }

        // 길이 확인
        if (codeVerifier.length() < CODE_VERIFIER_MIN_LENGTH ||
            codeVerifier.length() > CODE_VERIFIER_MAX_LENGTH) {
            log.warn("Code verifier length invalid: {}", codeVerifier.length());
            return false;
        }

        // 허용 문자 확인
        for (char c : codeVerifier.toCharArray()) {
            if (!VALID_CHARS.contains(String.valueOf(c))) {
                log.warn("Code verifier contains invalid character: {}", c);
                return false;
            }
        }

        return true;
    }
}
