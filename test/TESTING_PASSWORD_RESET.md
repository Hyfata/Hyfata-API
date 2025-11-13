# 비밀번호 재설정 (Password Reset) 테스트 가이드

이 문서는 Hyfata REST API의 비밀번호 재설정 기능을 테스트하는 방법을 설명합니다.

## 개요

사용자가 비밀번호를 잊은 경우 안전하게 재설정할 수 있는 기능입니다.

- **프로세스**: 요청 → 이메일 발송 → 링크/토큰 확인 → 비밀번호 재설정
- **재설정 토큰 유효기간**: 1시간
- **발송 방법**: 이메일

---

## 선행 조건

1. 애플리케이션 실행
```bash
./gradlew bootRun
```

2. 이메일 서비스 설정 완료 (application.properties)
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
app.frontend.url=http://localhost:3000
```

3. 테스트용 사용자 계정
   - 가입된 이메일 필요

---

## 테스트 1: 비밀번호 재설정 요청

### 요청
```bash
curl -X POST http://localhost:8080/api/auth/request-password-reset \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com"
  }'
```

### 예상 응답 (200 OK)
```json
{
  "message": "Password reset link has been sent to your email"
}
```

### 테스트 케이스

#### 1.1 정상 재설정 요청
- **입력**: 가입된 이메일
- **예상 결과**: 200 상태 코드, 성공 메시지
- **검증사항**:
  - 이메일 수신 확인
  - 재설정 링크 또는 토큰 포함
  - 토큰 형식 확인 (UUID)

#### 1.2 존재하지 않는 이메일
- **입력**: 가입되지 않은 이메일
- **예상 결과**: 200 상태 코드 (보안상 동일한 응답)
- **검증사항**:
  - 실제로는 이메일 발송 안 됨 (보안)
  - 사용자에게는 동일한 응답 반환

#### 1.3 유효하지 않은 이메일 형식
- **입력**: "invalid-email" 같은 잘못된 형식
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Invalid email format"
}
```

#### 1.4 필드 누락
- **입력**: email 필드 없음
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Email is required"
}
```

---

## 테스트 2: 비밀번호 재설정

### 요청
```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "token": "uuid-token-from-email",
    "newPassword": "NewPassword123!",
    "confirmPassword": "NewPassword123!"
  }'
```

### 예상 응답 (200 OK)
```json
{
  "message": "Password reset successful"
}
```

### 테스트 케이스

#### 2.1 정상 비밀번호 재설정
- **전제조건**: 유효한 재설정 토큰 필요 (이메일에서 받은 것)
- **입력**: 정확한 이메일, 토큰, 새 비밀번호
- **예상 결과**: 200 상태 코드, 성공 메시지
- **검증사항**:
  - 비밀번호가 변경됨
  - 새 비밀번호로 로그인 가능
  - 이전 비밀번호로는 로그인 불가
  - 비밀번호가 BCrypt 암호화됨

#### 2.2 유효하지 않은 토큰
- **입력**: 잘못되거나 존재하지 않는 토큰
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "error": "Invalid or expired reset token"
}
```

#### 2.3 만료된 토큰
- **입력**: 1시간 이상 지난 토큰
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "error": "Reset token has expired"
}
```

#### 2.4 비밀번호 불일치
- **입력**: newPassword와 confirmPassword가 다름
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Passwords do not match"
}
```

#### 2.5 약한 비밀번호
- **입력**: 8자 미만 또는 특수문자 없음
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Password does not meet security requirements"
}
```

#### 2.6 존재하지 않는 이메일
- **입력**: 가입되지 않은 이메일
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "error": "User not found"
}
```

#### 2.7 현재 비밀번호와 동일
- **입력**: 현재 사용 중인 비밀번호로 재설정 시도
- **예상 결과**: 400 상태 코드 (구현에 따라)
- **응답**:
```json
{
  "error": "New password cannot be the same as current password"
}
```

#### 2.8 필드 누락
- **입력**: 필수 필드 중 하나 이상 누락
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Required field is missing"
}
```

---

## 통합 테스트 시나리오

### 시나리오 1: 비밀번호 잊음 → 재설정 → 새 비밀번호로 로그인
1. 비밀번호 재설정 요청 (POST /api/auth/request-password-reset)
2. 이메일 확인 및 재설정 링크/토큰 획득
3. 비밀번호 재설정 (POST /api/auth/reset-password)
4. 새 비밀번호로 로그인 시도 (성공 확인)
5. 이전 비밀번호로 로그인 시도 (실패 확인)

### 시나리오 2: 재설정 토큰 만료
1. 비밀번호 재설정 요청
2. 1시간 대기 (또는 환경변수 조정)
3. 토큰으로 재설정 시도 (실패 확인)
4. 다시 재설정 요청

### 시나리오 3: 여러 번의 재설정 요청
1. 비밀번호 재설정 요청 (첫 번째)
2. 다시 비밀번호 재설정 요청 (두 번째)
3. 첫 번째 토큰으로 재설정 시도 (성공)
4. 두 번째 토큰으로 재설정 시도 (선택사항 - 구현에 따라)

---

## 자동화 테스트 (cURL 스크립트)

### password_reset_test.sh
```bash
#!/bin/bash

API_URL="http://localhost:8080"
EMAIL="testuser@example.com"
OLD_PASSWORD="TestPassword123!"
NEW_PASSWORD="NewPassword456!"

# 1. 비밀번호 재설정 요청
echo "=== 1단계: 비밀번호 재설정 요청 ==="
REQUEST_RESPONSE=$(curl -s -X POST $API_URL/api/auth/request-password-reset \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\"}")

echo "응답: $REQUEST_RESPONSE"

# 2. 이메일에서 토큰 확인 (수동으로 토큰 입력)
echo ""
echo "=== 2단계: 이메일 확인 ==="
read -p "이메일에서 받은 재설정 토큰을 입력하세요: " RESET_TOKEN

# 3. 비밀번호 재설정
echo ""
echo "=== 3단계: 비밀번호 재설정 ==="
RESET_RESPONSE=$(curl -s -X POST $API_URL/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\",
    \"token\": \"$RESET_TOKEN\",
    \"newPassword\": \"$NEW_PASSWORD\",
    \"confirmPassword\": \"$NEW_PASSWORD\"
  }")

echo "응답: $RESET_RESPONSE"

# 4. 새 비밀번호로 로그인 시도
echo ""
echo "=== 4단계: 새 비밀번호로 로그인 ==="
NEW_LOGIN=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$NEW_PASSWORD\"}")

echo "응답: $NEW_LOGIN"
NEW_ACCESS_TOKEN=$(echo $NEW_LOGIN | jq -r '.accessToken // .message')
echo "로그인 결과: $NEW_ACCESS_TOKEN"

# 5. 이전 비밀번호로 로그인 시도 (실패 예상)
echo ""
echo "=== 5단계: 이전 비밀번호로 로그인 시도 (실패 예상) ==="
OLD_LOGIN=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$OLD_PASSWORD\"}")

echo "응답: $OLD_LOGIN"
```

사용법:
```bash
chmod +x password_reset_test.sh
./password_reset_test.sh
```

---

## Postman 컬렉션

### 비밀번호 재설정 요청
```
Method: POST
URL: http://localhost:8080/api/auth/request-password-reset
Headers:
  - Content-Type: application/json
Body (JSON):
{
  "email": "testuser@example.com"
}
```

### 비밀번호 재설정
```
Method: POST
URL: http://localhost:8080/api/auth/reset-password
Headers:
  - Content-Type: application/json
Body (JSON):
{
  "email": "testuser@example.com",
  "token": "uuid-token-from-email",
  "newPassword": "NewPassword123!",
  "confirmPassword": "NewPassword123!"
}
```

---

## 이메일 테스트 팁

### Gmail을 사용하는 경우
1. 2단계 인증 활성화
2. [앱 비밀번호](https://myaccount.google.com/apppasswords) 생성
3. `application.properties`에 설정:
```properties
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

### 로컬 테스트 (MailHog 사용)
```bash
# MailHog 실행
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

# application.properties에 설정
spring.mail.host=localhost
spring.mail.port=1025
```

그 후 http://localhost:8025에서 발송된 이메일 확인 가능

---

## 보안 고려사항

1. **토큰 만료**: 1시간은 적절한 시간. 너무 길면 보안 위험, 너무 짧으면 UX 저하
2. **토큰 일회성**: 재설정 후 토큰은 무효화되어야 함
3. **이메일 검증**: 실제 사용자 이메일인지 확인
4. **Rate Limiting**: 재설정 요청 횟수 제한 고려
5. **로깅**: 민감한 정보(토큰) 로그 출력 금지

---

## 주의사항

1. **이메일 설정**: 메일 서버가 정상 작동해야 함
2. **스팸 폴더**: 이메일이 스팸으로 분류될 수 있음
3. **시간 동기화**: 서버의 정확한 시간 필요
4. **프론트엔드 연동**: app.frontend.url을 올바르게 설정하여 이메일의 링크가 정확하게 작동하도록 함

---

## 문제 해결

### 이메일 수신 안 됨
- SMTP 설정 확인 (호스트, 포트, 인증정보)
- 스팸 폴더 확인
- 메일 서비스 로그 확인
- 방화벽에서 SMTP 포트(587) 차단 여부 확인

### 재설정 토큰 유효 안 됨
- 토큰 복사 시 공백 확인
- 토큰 만료 시간 확인 (1시간)
- 토큰 형식 정확성 확인

### 비밀번호 재설정 후에도 이전 비밀번호로 로그인
- BCrypt 암호화 설정 확인
- 데이터베이스 업데이트 확인
- 캐시 문제 확인

### 같은 이메일로 여러 번 재설정 요청
- 이전 토큰 처리 방법 확인 (유효화? 무효화?)
- 구현에 따라 처리 로직 조정 필요

