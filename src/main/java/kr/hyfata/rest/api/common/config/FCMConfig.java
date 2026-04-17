package kr.hyfata.rest.api.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FCMConfig {

    @Value("${firebase.config.path:}")
    private String firebaseConfigPath;

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        try {
            if (firebaseConfigPath != null && !firebaseConfigPath.isEmpty()) {
                try (FileInputStream serviceAccount = new FileInputStream(firebaseConfigPath)) {
                    FirebaseOptions options = new FirebaseOptions.Builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();

                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp(options);
                    }
                    return FirebaseMessaging.getInstance();
                }
            } else {
                // Firebase 설정 파일이 없는 경우 (개발/테스트 환경)
                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseOptions options = new FirebaseOptions.Builder()
                            .setProjectId("dev-project")
                            .build();
                    FirebaseApp.initializeApp(options);
                }
                return FirebaseMessaging.getInstance();
            }
        } catch (IOException e) {
            System.err.println("경고: Firebase 초기화 실패. FCM 기능이 비활성화됩니다: " + e.getMessage());
            return null;
        }
    }
}
