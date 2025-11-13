# 2단계 인증 (2FA) 테스트 가이드

이 문서는 Hyfata REST API의 2단계 인증 (2FA) 기능을 테스트하는 방법을 설명합니다.

## 개요

2FA는 이메일 기반 인증 코드를 사용하여 추가 보안 계층을 제공합니다.

- **2FA 코드 유효기간**: 10분
- **발송 방법**: 이메일
- **지원 방식**: 로그인 시 2FA 활성화, 활성화/비활성화 관리

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
```

3. 테스트용 사용자 계정 준비
   - 유효한 이메일 주소 필요
   - 이메일 검증 완료 필요

---

## 테스트 1: 2FA 활성화

### 요청
```bash
curl -X POST http://localhost:8080/api/auth/enable-2fa \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json"
```

### 예상 응답 (200 OK)
```json
{
  "message": "Two-factor authentication enabled"
}
```

### 테스트 케이스

#### 1.1 정상 2FA 활성화
- **전제조건**: 유효한 accessToken 필요
- **요청**: 인증된 사용자로 요청
- **예상 결과**: 200 상태 코드, 성공 메시지
- **검증사항**:
  - 사용자의 2FA 설정이 활성화됨
  - 데이터베이스에 업데이트됨

#### 1.2 토큰 없이 요청
- **요청**: Authorization 헤더 없음
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "Unauthorized"
}
```

#### 1.3 유효하지 않은 토큰
- **요청**: 만료되었거나 잘못된 accessToken 사용
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "Invalid token"
}
```

---

## 테스트 2: 2FA 비활성화

### 요청
```bash
curl -X POST http://localhost:8080/api/auth/disable-2fa \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json"
```

### 예상 응답 (200 OK)
```json
{
  "message": "Two-factor authentication disabled"
}
```

### 테스트 케이스

#### 2.1 정상 2FA 비활성화
- **전제조건**: 2FA가 활성화된 계정, 유효한 accessToken
- **요청**: 인증된 사용자로 요청
- **예상 결과**: 200 상태 코드, 성공 메시지
- **검증사항**:
  - 사용자의 2FA 설정이 비활성화됨
  - 이후 로그인 시 2FA 코드 요청 없음

---

## 테스트 3: 2FA 검증

### 전제 조건
1. 2FA가 활성화된 계정
2. 로그인 시도하여 2FA 코드 수신

### 로그인 (2FA 활성화됨)

#### 요청
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "password": "TestPassword123!"
  }'
```

#### 응답 (200 OK)
```json
{
  "twoFactorRequired": true,
  "message": "Please check your email for the 2FA code"
}
```

- **검증사항**:
  - `twoFactorRequired: true` 반환
  - 이메일로 6자리 코드 발송됨
  - `accessToken`과 `refreshToken` 미포함

### 2FA 코드 검증

#### 요청
```bash
curl -X POST http://localhost:8080/api/auth/verify-2fa \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "code": "123456"
  }'
```

#### 예상 응답 (200 OK)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "twoFactorRequired": false,
  "message": "Login successful"
}
```

### 2FA 검증 테스트 케이스

#### 3.1 정상 2FA 코드 검증
- **입력**: 이메일로 수신한 정확한 6자리 코드
- **예상 결과**: 200 상태 코드, accessToken & refreshToken 반환
- **검증사항**:
  - 반환된 토큰 유효
  - `twoFactorRequired: false`
  - 코드 유효기간 내 사용 가능

#### 3.2 잘못된 2FA 코드
- **입력**: 잘못된 6자리 코드
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "Invalid 2FA code"
}
```

#### 3.3 유효기간 만료된 2FA 코드
- **입력**: 발송 후 10분 이상 경과한 코드
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "2FA code expired"
}
```

#### 3.4 존재하지 않는 사용자
- **입력**: 가입되지 않은 이메일
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "User not found"
}
```

#### 3.5 2FA 미활성화 사용자
- **입력**: 2FA가 비활성화된 사용자 정보로 검증 시도
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "message": "2FA not enabled for this user"
}
```

---

## 통합 테스트 시나리오

### 시나리오 1: 2FA 활성화 → 로그인 → 2FA 코드 검증
1. 로그인하여 accessToken 획득
2. 2FA 활성화 (POST /api/auth/enable-2fa)
3. 로그아웃
4. 다시 로그인 시도
5. 2FA 코드 메일 수신
6. 2FA 코드 검증 (POST /api/auth/verify-2fa)
7. 새 accessToken 획득 확인

### 시나리오 2: 2FA 비활성화 → 일반 로그인
1. 2FA 활성화된 계정으로 로그인
2. 2FA 코드 검증 후 accessToken 획득
3. 2FA 비활성화 (POST /api/auth/disable-2fa)
4. 다시 로그인 시도
5. 2FA 코드 요청 없이 즉시 토큰 반환 확인

---

## 자동화 테스트 (cURL 스크립트)

### 2fa_test.sh
```bash
#!/bin/bash

API_URL="http://localhost:8080"
EMAIL="testuser@example.com"
PASSWORD="TestPassword123!"

# 1. 로그인하여 accessToken 획득
echo "=== 로그인 ==="
LOGIN_RESPONSE=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$PASSWORD\"}")

ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.accessToken')
echo "AccessToken: $ACCESS_TOKEN"

# 2. 2FA 활성화
echo ""
echo "=== 2FA 활성화 ==="
ENABLE_2FA=$(curl -s -X POST $API_URL/api/auth/enable-2fa \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json")

echo "2FA 활성화 응답: $ENABLE_2FA"

# 3. 로그아웃 후 다시 로그인 (2FA 요청 예상)
echo ""
echo "=== 2FA 활성화 후 로그인 ==="
LOGIN_2FA=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$PASSWORD\"}")

echo "로그인 응답: $LOGIN_2FA"
echo "twoFactorRequired 값 확인: $(echo $LOGIN_2FA | jq -r '.twoFactorRequired')"

# 4. 2FA 코드 검증 (이메일에서 받은 코드 입력 필요)
read -p "이메일에서 받은 2FA 코드를 입력하세요: " 2FA_CODE

echo ""
echo "=== 2FA 코드 검증 ==="
VERIFY_2FA=$(curl -s -X POST $API_URL/api/auth/verify-2fa \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"code\": \"$2FA_CODE\"}")

echo "2FA 검증 응답: $VERIFY_2FA"
NEW_ACCESS_TOKEN=$(echo $VERIFY_2FA | jq -r '.accessToken')
echo "새 AccessToken: $NEW_ACCESS_TOKEN"

# 5. 2FA 비활성화
echo ""
echo "=== 2FA 비활성화 ==="
DISABLE_2FA=$(curl -s -X POST $API_URL/api/auth/disable-2fa \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN" \
  -H "Content-Type: application/json")

echo "2FA 비활성화 응답: $DISABLE_2FA"
```

사용법:
```bash
chmod +x 2fa_test.sh
./2fa_test.sh
```

---

## Postman 컬렉션

### 2FA 활성화
```
Method: POST
URL: http://localhost:8080/api/auth/enable-2fa
Headers:
  - Authorization: Bearer <accessToken>
  - Content-Type: application/json
```

### 2FA 비활성화
```
Method: POST
URL: http://localhost:8080/api/auth/disable-2fa
Headers:
  - Authorization: Bearer <accessToken>
  - Content-Type: application/json
```

### 2FA 코드 검증
```
Method: POST
URL: http://localhost:8080/api/auth/verify-2fa
Headers:
  - Content-Type: application/json
Body (JSON):
{
  "email": "testuser@example.com",
  "code": "123456"
}
```

---

## 주의사항

1. **2FA 코드 유효기간**: 발송 후 10분 이내에 검증해야 함
2. **이메일 설정**: 메일 서버가 정상 작동해야 코드 수신 가능
3. **토큰 타입**: 2FA 검증 후 획득한 토큰은 일반 로그인과 동일하게 사용 가능
4. **로그 보안**: 2FA 코드는 로그에 절대 출력되지 않도록 주의
5. **사용자 혼동 방지**: 2FA 활성화 후 로그인 프로세스 변경 안내 필요

---

## 문제 해결

### 2FA 코드 수신 안 됨
- SMTP 설정 확인
- 스팸 폴더 확인
- 이메일 주소 정확성 확인

### 2FA 검증 계속 실패
- 정확한 6자리 코드 입력 확인
- 유효기간 확인 (10분 초과 시 새 코드 요청)
- 2FA가 실제로 활성화되었는지 확인

### 2FA 활성화 실패
- JWT 토큰 유효성 확인
- 토큰 만료 여부 확인
- 사용자 정보 DB 상 존재 여부 확인

