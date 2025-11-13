# 회원가입 및 로그인 테스트 가이드

이 문서는 Hyfata REST API의 회원가입과 로그인 기능을 테스트하는 방법을 설명합니다.

## 사전 준비

### 1. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 2. 테스트 도구 준비
- **Postman** 또는 **cURL**을 사용하여 HTTP 요청 테스트
- 또는 **Thunder Client** (VS Code 확장)

### 3. 필수 설정 확인
`application.properties`에서 다음이 설정되어 있는지 확인:
```properties
spring.application.name=Hyfata-RestAPI
jwt.secret=your-secret-key-min-32-characters
jwt.expiration=86400000
app.frontend.url=http://localhost:3000
```

---

## 테스트 1: 회원가입

### 요청
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "username": "testuser",
    "password": "TestPassword123!",
    "confirmPassword": "TestPassword123!",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

### 예상 응답 (201 Created)
```json
{
  "message": "Registration successful. Please check your email to verify your account."
}
```

### 테스트 케이스

#### 1.1 정상 회원가입
- **입력**: 유효한 이메일, 비밀번호, 개인정보
- **예상 결과**: 201 상태 코드, 확인 메시지
- **검증사항**:
  - 데이터베이스에 사용자 생성됨
  - 인증 이메일 발송됨
  - 비밀번호 BCrypt 암호화됨

#### 1.2 중복 이메일
- **입력**: 이미 가입된 이메일
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Email already registered"
}
```

#### 1.3 비밀번호 불일치
- **입력**: password와 confirmPassword가 다름
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Passwords do not match"
}
```

#### 1.4 약한 비밀번호
- **입력**: 8자 미만 또는 특수문자 없음
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Password does not meet requirements"
}
```

#### 1.5 필드 누락
- **입력**: email, password 등 필수 필드 누락
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "Required field is missing"
}
```

---

## 테스트 2: 로그인

### 요청
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "password": "TestPassword123!"
  }'
```

### 예상 응답 (200 OK) - 2FA 미활성화 시
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

### 테스트 케이스

#### 2.1 정상 로그인 (2FA 미활성화)
- **입력**: 유효한 이메일, 정확한 비밀번호
- **예상 결과**: 200 상태 코드, accessToken & refreshToken 반환
- **검증사항**:
  - 반환된 토큰이 유효함
  - tokenType이 "Bearer"임
  - expiresIn이 86400000ms (24시간)

#### 2.2 이메일 검증 전 로그인
- **입력**: 이메일 인증되지 않은 계정으로 로그인
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "Email verification required"
}
```

#### 2.3 잘못된 비밀번호
- **입력**: 존재하는 이메일, 틀린 비밀번호
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "Invalid email or password"
}
```

#### 2.4 존재하지 않는 이메일
- **입력**: 가입되지 않은 이메일
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "Invalid email or password"
}
```

#### 2.5 비활성화된 계정
- **입력**: 비활성화된 계정으로 로그인 시도
- **예상 결과**: 401 상태 코드
- **응답**:
```json
{
  "message": "Account is disabled"
}
```

#### 2.6 정상 로그인 (2FA 활성화)
- **입력**: 2FA가 활성화된 계정으로 로그인
- **예상 결과**: 200 상태 코드
- **응답**:
```json
{
  "twoFactorRequired": true,
  "message": "Please check your email for the 2FA code"
}
```
- **검증사항**:
  - accessToken과 refreshToken이 반환되지 않음
  - twoFactorRequired = true
  - 이메일로 2FA 코드 발송됨

---

## 통합 테스트 시나리오

### 시나리오 1: 회원가입 → 이메일 검증 → 로그인
1. 회원가입 (POST /api/auth/register)
2. 이메일에서 검증 링크 클릭 또는 토큰 사용
3. 이메일 검증 (GET /api/auth/verify-email?token=xxx)
4. 로그인 (POST /api/auth/login)
5. 액세스 토큰 획득 검증

### 시나리오 2: 로그인 → 보호된 엔드포인트 접근
1. 로그인하여 accessToken 획득
2. 보호된 엔드포인트 호출 (예: GET /api/protected/user-info)
3. Authorization 헤더에 Bearer 토큰 포함
4. 성공적인 응답 확인

```bash
curl -X GET http://localhost:8080/api/protected/user-info \
  -H "Authorization: Bearer <accessToken>"
```

---

## 자동화 테스트 (cURL 스크립트)

### register_and_login_test.sh
```bash
#!/bin/bash

API_URL="http://localhost:8080"

# 1. 회원가입
echo "=== 회원가입 테스트 ==="
REGISTER_RESPONSE=$(curl -s -X POST $API_URL/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "username": "testuser",
    "password": "TestPassword123!",
    "confirmPassword": "TestPassword123!",
    "firstName": "John",
    "lastName": "Doe"
  }')

echo "회원가입 응답: $REGISTER_RESPONSE"

# 2. 로그인 시도 (이메일 검증 전 - 실패 예상)
echo ""
echo "=== 이메일 검증 전 로그인 테스트 (실패 예상) ==="
LOGIN_RESPONSE=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "password": "TestPassword123!"
  }')

echo "로그인 응답: $LOGIN_RESPONSE"

# 3. 이메일 검증 (실제로는 이메일 받아서 링크 클릭 또는 토큰 사용)
# GET /api/auth/verify-email?token=<token>

# 4. 로그인 성공 (이메일 검증 후)
echo ""
echo "=== 이메일 검증 후 로그인 테스트 (성공 예상) ==="
LOGIN_RESPONSE=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "password": "TestPassword123!"
  }')

echo "로그인 응답: $LOGIN_RESPONSE"

# accessToken 추출 (jq 필요)
ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.accessToken')
echo ""
echo "발급된 accessToken: $ACCESS_TOKEN"
```

사용법:
```bash
chmod +x register_and_login_test.sh
./register_and_login_test.sh
```

---

## Postman 컬렉션

### 회원가입 요청
```
Method: POST
URL: http://localhost:8080/api/auth/register
Headers:
  - Content-Type: application/json
Body (JSON):
{
  "email": "testuser@example.com",
  "username": "testuser",
  "password": "TestPassword123!",
  "confirmPassword": "TestPassword123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

### 로그인 요청
```
Method: POST
URL: http://localhost:8080/api/auth/login
Headers:
  - Content-Type: application/json
Body (JSON):
{
  "email": "testuser@example.com",
  "password": "TestPassword123!"
}
```

---

## 주의사항

1. **이메일 설정 필수**: 회원가입 시 이메일 검증이 필요하므로 메일 서버 설정이 완료되어야 함
2. **비밀번호 요구사항**: 최소 8자, 대소문자 + 숫자 + 특수문자 포함
3. **토큰 유효기간**:
   - Access Token: 24시간
   - Refresh Token: 7일
4. **HTTPS 사용**: 프로덕션 환경에서는 반드시 HTTPS 사용
5. **민감한 정보 보호**: 실제 프로덕션에서는 로그에 비밀번호를 절대 출력하지 않음

---

## 문제 해결

### 회원가입 시 이메일 발송 실패
- SMTP 설정 확인: `application.properties`
- 메일 서비스 가동 상태 확인
- 방화벽에서 SMTP 포트(587) 차단 여부 확인

### 로그인 시 "Invalid email or password"
- 정확한 이메일 및 비밀번호 입력 확인
- 이메일이 검증되었는지 확인
- 계정이 비활성화되지 않았는지 확인

### 토큰 만료 에러
- Refresh Token 사용하여 새 accessToken 획득
- 또는 다시 로그인

