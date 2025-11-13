# 이메일 검증 (Email Verification) 테스트 가이드

이 문서는 Hyfata REST API의 이메일 검증 기능을 테스트하는 방법을 설명합니다.

## 개요

회원가입 후 사용자는 이메일로 수신한 검증 링크를 통해 계정을 활성화해야 합니다.

- **검증 토큰 유효기간**: 24시간 (또는 설정된 기간)
- **검증 방법**: 이메일 링크 클릭 또는 토큰 전달
- **검증 전**: 로그인 불가능

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

3. 회원가입 완료한 사용자
   - 검증 이메일 수신 가능한 이메일 주소

---

## 테스트 1: 정상 이메일 검증

### 요청
```bash
curl -X GET "http://localhost:8080/api/auth/verify-email?token=verification-token-123456"
```

### 예상 응답 (200 OK)
```json
{
  "message": "Email verified successfully"
}
```

### 테스트 케이스

#### 1.1 정상 토큰으로 검증
- **전제조건**: 회원가입 후 수신한 유효한 검증 토큰
- **요청**: 토큰 포함한 GET 요청
- **예상 결과**: 200 상태 코드, 성공 메시지
- **검증사항**:
  - 사용자 계정의 emailVerified 플래그가 true로 변경
  - 이제 로그인 가능
  - 같은 토큰으로 재요청 시 이미 검증됨 메시지

#### 1.2 이미 검증된 이메일로 재검증
- **전제조건**: 이미 검증된 계정
- **요청**: 새로운 검증 토큰으로 요청 (있다면)
- **예상 결과**: 200 상태 코드 (또는 이미 검증됨 메시지)
- **응답**:
```json
{
  "message": "Email already verified"
}
```

---

## 테스트 2: 이메일 검증 실패 케이스

### 2.1 유효하지 않은 토큰

#### 요청
```bash
curl -X GET "http://localhost:8080/api/auth/verify-email?token=invalid-token"
```

#### 예상 응답 (400 Bad Request)
```json
{
  "error": "Invalid verification token"
}
```

### 2.2 만료된 토큰

#### 요청
```bash
curl -X GET "http://localhost:8080/api/auth/verify-email?token=expired-token-123456"
```

#### 예상 응답 (400 Bad Request)
```json
{
  "error": "Verification token has expired"
}
```

#### 테스트 방법
- 24시간 이상 경과한 토큰으로 요청
- 또는 환경변수에서 토큰 만료 시간을 짧게 설정 후 대기

### 2.3 토큰 누락

#### 요청
```bash
curl -X GET "http://localhost:8080/api/auth/verify-email"
```

#### 예상 응답 (400 Bad Request)
```json
{
  "error": "Verification token is required"
}
```

### 2.4 손상된 토큰

#### 요청
```bash
curl -X GET "http://localhost:8080/api/auth/verify-email?token=corrupted%20token%20data"
```

#### 예상 응답 (400 Bad Request)
```json
{
  "error": "Invalid verification token"
}
```

### 2.5 다른 사용자의 토큰 사용

#### 요청
```bash
curl -X GET "http://localhost:8080/api/auth/verify-email?token=user2-verification-token"
```

#### 예상 응답 (400 Bad Request)
```json
{
  "error": "Invalid verification token"
}
```

---

## 통합 테스트 시나리오

### 시나리오 1: 회원가입 → 이메일 검증 → 로그인
1. 회원가입 (POST /api/auth/register)
   - "Registration successful. Please check your email..." 응답
2. 이메일 확인 및 검증 링크/토큰 획득
3. 이메일 검증 (GET /api/auth/verify-email?token=xxx)
   - "Email verified successfully" 응답
4. 로그인 시도 (POST /api/auth/login)
   - 성공 (accessToken & refreshToken 획득)

### 시나리오 2: 검증 전 로그인 시도
1. 회원가입 (이메일 검증 받음)
2. 로그인 시도 (검증 전)
   - 실패: "Email verification required"
3. 이메일 검증 (GET /api/auth/verify-email?token=xxx)
4. 로그인 다시 시도
   - 성공

### 시나리오 3: 토큰 만료
1. 회원가입
2. 24시간 대기
3. 이메일 검증 시도 (토큰 만료)
   - 실패: "Verification token has expired"
4. 재가입 또는 새로운 검증 이메일 요청

---

## 자동화 테스트 (cURL 스크립트)

### email_verification_test.sh
```bash
#!/bin/bash

API_URL="http://localhost:8080"
EMAIL="testuser@example.com"
USERNAME="testuser"
PASSWORD="TestPassword123!"
FIRST_NAME="John"
LAST_NAME="Doe"

# 1. 회원가입
echo "=== 1단계: 회원가입 ==="
REGISTER_RESPONSE=$(curl -s -X POST $API_URL/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\",
    \"username\": \"$USERNAME\",
    \"password\": \"$PASSWORD\",
    \"confirmPassword\": \"$PASSWORD\",
    \"firstName\": \"$FIRST_NAME\",
    \"lastName\": \"$LAST_NAME\"
  }")

echo "회원가입 응답: $REGISTER_RESPONSE"

# 2. 검증 전 로그인 시도 (실패 예상)
echo ""
echo "=== 2단계: 검증 전 로그인 시도 (실패 예상) ==="
LOGIN_BEFORE=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$PASSWORD\"}")

echo "로그인 응답: $LOGIN_BEFORE"

# 3. 이메일 검증 토큰 입력 받기
echo ""
echo "=== 3단계: 이메일 검증 ==="
read -p "이메일에서 받은 검증 토큰을 입력하세요: " VERIFY_TOKEN

VERIFY_RESPONSE=$(curl -s -X GET "$API_URL/api/auth/verify-email?token=$VERIFY_TOKEN")
echo "검증 응답: $VERIFY_RESPONSE"

# 4. 검증 후 로그인 (성공 예상)
echo ""
echo "=== 4단계: 검증 후 로그인 (성공 예상) ==="
LOGIN_AFTER=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$PASSWORD\"}")

echo "로그인 응답: $LOGIN_AFTER"
ACCESS_TOKEN=$(echo $LOGIN_AFTER | jq -r '.accessToken // "failed"')
echo "AccessToken 획득: $ACCESS_TOKEN"

# 5. 이미 검증된 이메일로 재검증 시도
echo ""
echo "=== 5단계: 이미 검증된 이메일 재검증 (실패 또는 성공 메시지) ==="
REVERIFY_RESPONSE=$(curl -s -X GET "$API_URL/api/auth/verify-email?token=$VERIFY_TOKEN")
echo "재검증 응답: $REVERIFY_RESPONSE"
```

사용법:
```bash
chmod +x email_verification_test.sh
./email_verification_test.sh
```

---

## Postman 컬렉션

### 이메일 검증
```
Method: GET
URL: http://localhost:8080/api/auth/verify-email?token=verification-token-123456
```

---

## 이메일 내용 확인

### 회원가입 후 수신 이메일 형식

일반적으로 다음과 같은 링크가 포함됩니다:

```
http://localhost:3000/verify?token=uuid-token-123456
또는
http://api.hyfata.com/api/auth/verify-email?token=uuid-token-123456
```

---

## 로컬 테스트 (MailHog 사용)

### MailHog 설정
```bash
# MailHog 컨테이너 실행
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

# application.properties 설정
spring.mail.host=localhost
spring.mail.port=1025
```

### 이메일 확인
- 웹 인터페이스: http://localhost:8025
- 발송된 모든 이메일 확인 가능
- 토큰 추출 용이

---

## 테스트 환경 설정

### 토큰 만료 시간 단축 (테스트용)

`application.properties`에 다음 추가:

```properties
# 기본값: 86400000ms (24시간)
# 테스트용: 300000ms (5분)
email.verification.expiration=300000
```

이렇게 설정하면 빠르게 토큰 만료 테스트 가능

---

## 보안 고려사항

1. **토큰 복잡성**: 무작위 생성된 UUID 사용으로 예측 불가능
2. **토큰 일회성**: 검증 후 토큰은 재사용 불가
3. **토큰 만료**: 충분한 시간(24시간) 제공하되, 너무 길지 않게
4. **HTTPS 사용**: 프로덕션에서는 필수 (토큰이 URL에 포함됨)
5. **Rate Limiting**: 검증 시도 횟수 제한 고려

---

## 주의사항

1. **이메일 발송**: 메일 서버 설정이 완료되어야 함
2. **스팸 필터**: 발송된 이메일이 스팸으로 분류될 수 있음
3. **URL 구성**: 이메일의 링크가 올바른 도메인을 가리키도록 설정
4. **타임아웃**: 토큰 만료 시간이 너무 짧으면 사용자 불편 야기

---

## 문제 해결

### 검증 이메일 수신 안 됨
- SMTP 설정 확인
- 메일 서비스 로그 확인
- 스팸 폴더 확인
- 방화벽 SMTP 포트(587) 차단 확인

### 토큰으로 검증 실패
- 토큰 복사 시 공백 확인
- 토큰 유효기간 확인 (24시간)
- 토큰 형식 정확성 확인
- URL 인코딩 문제 확인

### 검증 후에도 로그인 불가
- 데이터베이스에서 emailVerified 필드 확인
- 사용자 정보 조회 및 상태 확인

### 같은 이메일로 여러 번 가입
- 중복 이메일 체크 로직 확인
- 검증 전 가입된 이메일 처리 방법 확인

---

## 추가 기능 고려사항

### 검증 이메일 재발송
사용자가 이메일을 못 받은 경우를 대비하여 재발송 기능 추가 고려:

```bash
POST /api/auth/resend-verification-email
{
  "email": "testuser@example.com"
}
```

### 검증 상태 조회
특정 사용자의 검증 상태를 조회하는 엔드포인트 추가 고려:

```bash
GET /api/auth/verification-status
Authorization: Bearer <accessToken>
```

