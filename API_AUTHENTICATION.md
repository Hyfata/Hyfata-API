# Hyfata API - 인증 시스템 문서

이 문서는 구현된 보안 로그인 기능의 API 엔드포인트와 사용법을 설명합니다.

## 개요

Hyfata REST API는 다음의 보안 기능을 포함한 완전한 인증 시스템을 제공합니다:

- **JWT 기반 토큰 인증**: 상태 비저장(stateless) 인증
- **BCrypt 비밀번호 암호화**: 안전한 비밀번호 저장
- **2단계 인증 (2FA)**: 이메일 기반 추가 보안
- **토큰 갱신 (Refresh Token)**: 자동 토큰 갱신 메커니즘
- **비밀번호 재설정**: 이메일 기반 안전한 재설정
- **이메일 검증**: 회원가입 후 이메일 인증

## API 엔드포인트

### 1. 회원가입 (Register)

**요청:**
```
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "johndoe",
  "password": "SecurePassword123!",
  "confirmPassword": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**응답 (201 Created):**
```json
{
  "message": "Registration successful. Please check your email to verify your account."
}
```

**에러 응답 (400 Bad Request):**
```json
{
  "error": "Email already registered"
}
```

---

### 2. 로그인 (Login)

**요청:**
```
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePassword123!"
}
```

**응답 (200 OK) - 2FA 미활성화 시:**
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

**응답 (200 OK) - 2FA 활성화 시:**
```json
{
  "twoFactorRequired": true,
  "message": "Please check your email for the 2FA code"
}
```

**에러 응답 (401 Unauthorized):**
```json
{
  "message": "Invalid email or password"
}
```

---

### 3. 2FA 검증 (Verify Two-Factor Authentication)

2FA가 활성화된 사용자는 로그인 후 이 엔드포인트를 통해 2FA 코드를 검증합니다.

**요청:**
```
POST /api/auth/verify-2fa
Content-Type: application/json

{
  "email": "user@example.com",
  "code": "123456"
}
```

**응답 (200 OK):**
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

**에러 응답 (401 Unauthorized):**
```json
{
  "message": "Invalid 2FA code"
}
```

---

### 4. 토큰 갱신 (Refresh Token)

Access Token이 만료된 경우, Refresh Token을 사용하여 새로운 토큰을 발급받습니다.

**요청:**
```
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**응답 (200 OK):**
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

---

### 5. 비밀번호 재설정 요청 (Request Password Reset)

**요청:**
```
POST /api/auth/request-password-reset
Content-Type: application/json

{
  "email": "user@example.com"
}
```

**응답 (200 OK):**
```json
{
  "message": "Password reset link has been sent to your email"
}
```

---

### 6. 비밀번호 재설정 (Reset Password)

이메일에서 받은 재설정 토큰과 새 비밀번호를 사용합니다.

**요청:**
```
POST /api/auth/reset-password
Content-Type: application/json

{
  "email": "user@example.com",
  "token": "uuid-token-123456789",
  "newPassword": "NewPassword123!",
  "confirmPassword": "NewPassword123!"
}
```

**응답 (200 OK):**
```json
{
  "message": "Password reset successful"
}
```

---

### 7. 이메일 검증 (Verify Email)

회원가입 시 받은 검증 링크의 토큰을 사용합니다.

**요청:**
```
GET /api/auth/verify-email?token=verification-token-123456
```

**응답 (200 OK):**
```json
{
  "message": "Email verified successfully"
}
```

---

### 8. 2FA 활성화 (Enable Two-Factor Authentication)

인증된 사용자만 접근 가능합니다.

**요청:**
```
POST /api/auth/enable-2fa
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**응답 (200 OK):**
```json
{
  "message": "Two-factor authentication enabled"
}
```

---

### 9. 2FA 비활성화 (Disable Two-Factor Authentication)

인증된 사용자만 접근 가능합니다.

**요청:**
```
POST /api/auth/disable-2fa
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**응답 (200 OK):**
```json
{
  "message": "Two-factor authentication disabled"
}
```

---

## 인증된 요청

모든 보호된 엔드포인트에는 `Authorization` 헤더에 JWT 토큰을 포함해야 합니다.

**예시:**
```
GET /api/protected-endpoint
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNjc0NDQ3ODAwLCJleHAiOjE2NzQ1MzQyMDB9.xxxxxxxxxxx
```

## 보안 사항

### 비밀번호 요구사항
- 최소 8자 이상
- 권장: 대문자, 소문자, 숫자, 특수문자 포함

### 토큰 타임아웃
- **Access Token**: 24시간
- **Refresh Token**: 7일

### 2FA 코드 유효기간
- 10분

### 비밀번호 재설정 링크 유효기간
- 1시간

## 에러 코드

| HTTP Status | 설명 |
|-----------|------|
| 200 | 성공 |
| 201 | 생성됨 (회원가입) |
| 400 | 잘못된 요청 (검증 실패) |
| 401 | 인증 실패 |
| 500 | 서버 오류 |

## 환경 설정

`application.properties`에서 다음을 설정하세요:

```properties
# JWT 설정
jwt.secret=your-secret-key-min-32-characters
jwt.expiration=86400000
jwt.refresh-expiration=604800000

# 메일 설정
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password

# 프론트엔드 URL
app.frontend.url=http://localhost:3000
```

## 이메일 설정 (Gmail 기준)

1. [Gmail 설정](https://myaccount.google.com/security)에서 2단계 인증 활성화
2. [앱 비밀번호](https://myaccount.google.com/apppasswords) 생성
3. `application.properties`에서 비밀번호 설정

## 구현 시 주의사항

1. **JWT Secret 변경**: 프로덕션 환경에서는 환경 변수로 관리하세요
2. **HTTPS 사용**: 프로덕션에서는 반드시 HTTPS를 사용하세요
3. **CORS 설정**: 필요에 따라 CORS를 구성하세요
4. **Rate Limiting**: 로그인 시도 횟수 제한을 고려하세요
5. **로깅 및 모니터링**: 의심스러운 활동을 모니터링하세요

## 클라이언트 구현 예시 (JavaScript/React)

```javascript
// 로그인
async function login(email, password) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  return response.json();
}

// 인증된 요청
async function getProtectedData() {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('/api/protected-endpoint', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return response.json();
}

// 토큰 갱신
async function refreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  });
  const data = response.json();
  localStorage.setItem('accessToken', data.accessToken);
  return data.accessToken;
}
```

---

## 지원

문제가 발생하면 다음을 확인하세요:

1. 데이터베이스 마이그레이션 완료 여부
2. 메일 서비스 설정 올바른지 확인
3. JWT Secret이 32자 이상인지 확인
4. 방화벽에서 SMTP 포트(587) 차단되지 않았는지 확인
