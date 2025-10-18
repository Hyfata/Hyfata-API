# 메일 서버 설정 가이드

## 📧 개요

Hyfata REST API는 **POP3/SMTP 방식의 커스텀 메일 서버**를 사용합니다.

**메일 서버 정보:**
- **Host**: `mail.hyfata.kr`
- **SMTP Port**: 587
- **Protocol**: POP3/SMTP
- **Sender Email**: `noreply@hyfata.kr`
- **Authentication**: 사용자명/비밀번호

---

## ⚙️ 설정 파일

### application.properties (프로덕션)

```properties
# Mail Configuration (POP3/SMTP - mail.hyfata.kr)
spring.mail.enabled=true
spring.mail.host=mail.hyfata.kr
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:noreply@hyfata.kr}
spring.mail.password=${MAIL_PASSWORD:your-password}

# SMTP 설정
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=false
spring.mail.properties.mail.smtp.starttls.required=false
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000

# 발송자 이메일
spring.mail.from=noreply@hyfata.kr
```

### 환경 변수 설정

```bash
# .env 파일 또는 시스템 환경 변수로 설정
export MAIL_USERNAME=noreply@hyfata.kr
export MAIL_PASSWORD=your-mail-server-password
```

### Docker Compose 환경 변수

```yaml
environment:
  MAIL_USERNAME: noreply@hyfata.kr
  MAIL_PASSWORD: your-mail-server-password
```

---

## 🔐 보안 설정

### 1. TLS/SSL 설정

```properties
# STARTTLS 비활성화 (POP3/SMTP는 일반적으로 TLS를 사용하지 않음)
spring.mail.properties.mail.smtp.starttls.enable=false
spring.mail.properties.mail.smtp.starttls.required=false
```

### 2. 인증 정보 보호

**❌ 절대 금지:**
```properties
# credentials를 하드코딩하면 안됨!
spring.mail.username=noreply@hyfata.kr
spring.mail.password=MySecretPassword
```

**✅ 올바른 방법:**
```bash
# 1. 환경 변수 사용
export MAIL_USERNAME=noreply@hyfata.kr
export MAIL_PASSWORD=${SECURE_PASSWORD}

# 2. 또는 properties 파일 사용
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}

# 3. Kubernetes Secrets 사용
kubectl create secret generic mail-credentials \
  --from-literal=username=noreply@hyfata.kr \
  --from-literal=password=your-password
```

---

## 🔧 메일 서비스 작동 원리

### 비동기 이메일 발송

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Async  // 비동기 처리
    public void sendTwoFactorEmail(String to, String code) {
        try {
            if (!mailEnabled) {
                log.warn("Mail is disabled");
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@hyfata.kr");
            message.setTo(to);
            message.setSubject("Your 2FA Code");
            message.setText("Code: " + code);

            mailSender.send(message);  // SMTP로 발송
            log.info("Email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send email: {}", e.getMessage());
            // 이메일 실패는 비즈니스 로직에 영향 없음
        }
    }
}
```

### 스레드풀 설정

```properties
# 동시에 처리할 수 있는 메일 개수
spring.task.execution.pool.core-size=2        # 기본 스레드 2개
spring.task.execution.pool.max-size=5         # 최대 스레드 5개
spring.task.execution.pool.queue-capacity=100 # 큐 크기
```

---

## 📤 발송 시나리오

### 1. 회원가입 시 이메일 검증

```
클라이언트 요청
    ↓
AuthController.register()
    ↓
AuthService.register()
    ↓
User 생성 → DB 저장
    ↓
EmailService.sendEmailVerificationEmail() (비동기)
    ↓
메일 서버 (mail.hyfata.kr:587) 로 연결
    ↓
SMTP 인증 (noreply@hyfata.kr + 비밀번호)
    ↓
이메일 발송
    ↓
즉시 응답 (50ms)

백그라운드에서 이메일 발송 진행 중...
```

### 2. 2FA 코드 발송

```
로그인 요청
    ↓
AuthService.login() - 비밀번호 검증
    ↓
2FA 활성화 확인
    ↓
2FA 코드 생성 → DB 저장
    ↓
EmailService.sendTwoFactorEmail() (비동기)
    ↓
메일 서버로 발송
    ↓
응답: "Please check your email for 2FA code"
```

### 3. 비밀번호 재설정

```
비밀번호 재설정 요청
    ↓
AuthService.requestPasswordReset()
    ↓
재설정 토큰 생성 → DB 저장 (1시간 유효)
    ↓
EmailService.sendPasswordResetEmail() (비동기)
    ↓
이메일 본문에 재설정 링크 포함
    ↓
메일 발송
    ↓
사용자는 이메일의 링크 클릭 → 프론트엔드 이동
    ↓
새 비밀번호 입력 → 서버로 전송
```

---

## 🧪 테스트 환경

### application-test.properties

```properties
# 테스트 환경에서는 메일 발송 비활성화
spring.mail.enabled=false
spring.mail.host=localhost
spring.mail.port=3025
spring.mail.username=test@example.com
spring.mail.password=testpassword
spring.mail.from=test@example.com
```

**효과:**
- 실제 메일 발송 안 함
- 테스트 환경에서 SMTP 연결 불필요
- 단위 테스트 격리 가능

### 테스트 실행

```bash
# 테스트 환경으로 실행
./gradlew test -Dspring.profiles.active=test

# 또는 테스트 클래스에서
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {
    // ...
}
```

---

## 📊 메일 발송 흐름도

```
                  ┌─────────────────────────────┐
                  │   HTTP Request              │
                  │   /api/auth/register        │
                  └──────────────┬──────────────┘
                                 │
                    ┌────────────▼──────────────┐
                    │   AuthController          │
                    │   register()              │
                    └────────────┬──────────────┘
                                 │
                    ┌────────────▼──────────────┐
                    │   AuthService            │
                    │   register()             │
                    └────────────┬──────────────┘
                                 │
                    ┌────────────▼──────────────┐
                    │   User Entity 생성        │
                    │   & DB 저장              │
                    └────────────┬──────────────┘
                                 │
          ┌──────────────────────▼──────────────────────────┐
          │                                                  │
    ┌─────▼──────────┐                        ┌─────────────▼──────────┐
    │ 메인 스레드    │                        │   비동기 스레드        │
    │                │                        │   (Thread Pool)        │
    │ HTTP Response  │                        │                        │
    │ 즉시 반환      │                        │   EmailService        │
    │ (50ms)         │                        │   .sendEmail()        │
    │                │                        │                        │
    │ {              │                        │   Mail Server         │
    │   "message":   │                        │   mail.hyfata.kr:587  │
    │   "success"    │                        │                        │
    │ }              │                        │   → SMTP Auth         │
    │                │                        │   → Send Email        │
    └─────────────────                        │   → Log Result        │
                                              └───────────────────────┘
                                                      ↓
                                             이메일 발송 완료 (2초)
```

---

## 🚨 문제 해결

### 1. 메일 연결 실패

```
Error: Mail server connection refused (mail.hyfata.kr:587)
```

**해결 방법:**
- mail.hyfata.kr 호스트 접근 가능 확인
- 방화벽에서 587 포트 허용 확인
- 네트워크 연결 상태 확인

```bash
# 연결 테스트
telnet mail.hyfata.kr 587

# 또는
nc -zv mail.hyfata.kr 587
```

### 2. 인증 실패

```
Error: Authentication failed (535 5.7.8 Error: authentication failed)
```

**해결 방법:**
- 사용자명 확인: `noreply@hyfata.kr`
- 비밀번호 확인
- 환경 변수 설정 확인

```bash
# 환경 변수 확인
echo $MAIL_USERNAME
echo $MAIL_PASSWORD
```

### 3. 이메일 발송 안 됨

```
Error: No email received after sending request
```

**해결 방법:**
- `spring.mail.enabled=true` 확인
- 받는 사람 이메일 주소 확인
- 메일 서버 스팸 필터 확인

### 4. 타임아웃

```
Error: Socket timeout (timeout=5000ms)
```

**해결 방법:**
```properties
# 타임아웃 값 증가
spring.mail.properties.mail.smtp.connectiontimeout=10000
spring.mail.properties.mail.smtp.timeout=10000
```

---

## 📈 모니터링

### 이메일 발송 로그

```properties
# logging.properties
logging.level.org.springframework.mail=DEBUG
logging.level.kr.hyfata.rest.api.service.EmailService=DEBUG
```

### 로그 확인

```bash
# 실시간 로그 모니터링
tail -f logs/application.log | grep -i email

# 또는 Docker
docker logs container-name | grep -i email
```

### 성공 로그
```
[INFO] 2FA email sent successfully to: user@hyfata.kr
```

### 실패 로그
```
[ERROR] Failed to send 2FA email to user@hyfata.kr: Connection timeout
```

---

## 🔐 보안 체크리스트

- [ ] MAIL_USERNAME, MAIL_PASSWORD를 환경 변수로 설정
- [ ] 커밋에 메일 자격증명 포함 안 함
- [ ] SMTP 연결 암호화 확인
- [ ] 메일 서버 접근 제한 확인
- [ ] 로그에서 민감한 정보 제거
- [ ] 메일 발송 실패 시 사용자에게 적절한 메시지 표시

---

## 💡 팁

1. **비동기 발송**: 이메일 발송은 비동기로 처리되어 API 응답에 영향 없음
2. **메일 활성화/비활성화**: 개발 환경에서는 `spring.mail.enabled=false`로 설정
3. **타임아웃 조정**: 느린 네트워크에서는 타임아웃 값 증가
4. **배치 발송**: 대량의 메일 발송 시 배치 처리 고려
5. **템플릿**: HTML 이메일은 SimpleMailMessage 대신 MimeMessage 사용

---

## 📚 참고 자료

- [Spring Mail 공식 문서](https://spring.io/guides/gs/sending-email/)
- [Jakarta Mail API](https://jakarta.ee/specifications/mail/)
- [SMTP 프로토콜](https://tools.ietf.org/html/rfc5321)
