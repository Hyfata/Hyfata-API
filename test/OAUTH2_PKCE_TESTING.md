# OAuth 2.0 + PKCE 완전한 테스트 가이드

Hyfata REST API의 전체 비즈니스 로직을 **회원가입부터 OAuth 2.0 로그인까지** 순서대로 테스트하는 완전한 가이드입니다.

---

## 📌 개요

이 가이드는 **실제 서비스 출시 시나리오**에 맞춰 다음을 순서대로 테스트합니다:

1. ✅ OAuth 클라이언트 등록 (앱/웹사이트 등록)
2. ✅ 사용자 회원가입
3. ✅ 이메일 검증
4. ✅ OAuth 2.0 + PKCE 로그인
5. ✅ Access Token으로 API 호출
6. ✅ Refresh Token으로 토큰 갱신

---

## 🚀 시작하기

### 1. 애플리케이션 실행
```bash
cd /Users/najoan/IdeaProjects/Hyfata-RestAPI
./gradlew bootRun
```

### 2. Postman Collection Import
- 본 디렉토리의 `OAuth2_PKCE_Complete_Testing.json` 파일을 Postman에 import

### 3. 준비 완료
이제 아무 데이터도 없는 깨끗한 상태에서 **순서대로** 테스트를 진행합니다.

---

## 📋 전체 테스트 플로우

```
🔹 Section 0: Setup
   └─ OAuth 클라이언트 등록 (앱/웹사이트 등록)

🔹 Section 1: User Registration
   └─ 회원가입 API 호출

🔹 Section 2: Email Verification
   └─ 이메일 검증 (DB 업데이트 또는 API 호출)

🔹 Section 3: OAuth 2.0 + PKCE Login Flow (SAS)
   ├─ 3-1. Generate Code Challenge
   ├─ 3-2. Authorization Request (302 → 로그인 페이지)
   ├─ 3-3. Login (formLogin, email/password만 전송)
   ├─ 3-3b. Resume Authorization (Authorization Code 획득)
   └─ 3-4. Token Exchange (Basic 인증, Access Token 획득 + 세션 생성)

🔹 Section 4: Token Usage & Session Management
   ├─ 4-1. OAuth Refresh Token (토큰 갱신 + 세션 로테이션)
   ├─ 4-2. OAuth Logout (로그아웃 + 세션 무효화)
   ├─ 4-3. Protected Resource Access (선택사항)
   ├─ 4-4. Get Active Sessions (세션 목록 조회)
   └─ 4-5. Revoke Session (원격 로그아웃)

🔹 Section 5: Error Tests
   └─ PKCE 관련 에러 케이스
```

---

## Section 0️⃣: Setup - OAuth 클라이언트 등록

### 왜 필요한가?
- OAuth 2.0을 사용하려면 먼저 **앱(클라이언트)**을 등록해야 합니다
- 예: "나의 모바일 앱", "나의 웹사이트" 등록
- 등록 후 `client_id`와 `client_secret`을 받습니다

### Postman Request: `0-1. Register OAuth Client`

**Method**: POST
**URL**: `{{base_url}}/api/clients/register`
**Body** (JSON):
```json
{
  "name": "Test Mobile App",
  "description": "OAuth 2.0 테스트용 모바일 앱",
  "frontendUrl": "http://localhost:3000",
  "redirectUris": [
    "http://localhost:3000/callback",
    "http://localhost:3001/callback"
  ]
}
```

**Expected Response** (201 Created):
```json
{
  "message": "Client registered successfully",
  "client": {
    "id": 1,
    "clientId": "client_abc123...",
    "clientSecret": "secret_xyz789...",
    "name": "Test Mobile App",
    "redirectUris": ["http://localhost:3000/callback", "http://localhost:3001/callback"],
    ...
  }
}
```

**중요**: 응답에서 `clientId`와 `clientSecret`을 복사해서 Postman 변수에 저장하세요!

---

## Section 1️⃣: User Registration - 회원가입

### Postman Request: `1-1. User Registration`

**Method**: POST
**URL**: `{{base_url}}/api/auth/register`
**Body** (JSON):
```json
{
  "email": "testuser@example.com",
  "username": "testuser",
  "password": "TestPassword123!",
  "firstName": "Test",
  "lastName": "User"
}
```

**Expected Response** (201 Created):
```json
{
  "message": "Registration successful. Please check your email to verify your account."
}
```

**주의**:
- 이 시점에서는 아직 로그인할 수 없습니다 (이메일 검증 필요)
- 실제로는 이메일로 검증 링크가 발송되지만, 테스트 환경에서는 DB에서 직접 검증합니다

---

## Section 2️⃣: Email Verification - 이메일 검증

### 방법 1: 데이터베이스에서 직접 검증 (추천)

**SQL 실행** (DB 클라이언트 또는 pgAdmin):
```sql
UPDATE users
SET email_verified = true
WHERE email = 'testuser@example.com';
```

**확인**:
```sql
SELECT email, email_verified, enabled
FROM users
WHERE email = 'testuser@example.com';
```

### 방법 2: API 호출 (이메일 서버 설정되어 있을 경우)

실제 서비스에서는:
1. 이메일로 검증 링크 발송
2. 사용자가 링크 클릭: `GET /api/auth/verify-email?token=xxx`
3. 계정 활성화

**테스트 환경에서는 방법 1 사용을 권장합니다.**

---

## Section 3️⃣: OAuth 2.0 + PKCE Login Flow

이제 회원가입과 이메일 검증이 완료되었으므로, OAuth 2.0 로그인을 시작합니다.

### 3-1. Generate Code Challenge

**Postman Request**: `3-1. Generate Code Challenge`

**특징**:
- 실제 HTTP 요청을 보내지 않음
- Pre-request Script만 실행됨
- `code_verifier`와 `code_challenge` 자동 생성

**동작**:
1. `code_verifier` 생성 (128자 무작위 문자열)
2. `code_challenge = Base64URL(SHA256(code_verifier))`
3. `state` 생성 (CSRF 방지용)
4. Postman 변수에 저장

**실행 후 확인**:
- Postman Console에서 생성된 값 확인
- Variables 탭에서 `code_verifier`, `code_challenge`, `state` 확인

---

### 3-2. Authorization Request (브라우저 단계)

**Postman Request**: `3-2. Authorization Request (with PKCE)`

**Method**: GET
**URL**: `{{base_url}}/oauth/authorize`
**Query Params**:
```
client_id={{client_id}}
redirect_uri={{redirect_uri}}
response_type=code
state={{state}}
code_challenge={{code_challenge}}
code_challenge_method=S256
```

**Expected Response** (302 Found):
```
Location: /oauth/login
```

**SAS 동작**:
- authorize 요청을 세션(request cache)에 저장하고 로그인 페이지로 리다이렉트합니다
- 로그인 성공 후 저장된 요청이 자동 재개되므로, 로그인 폼에 OAuth 파라미터를 전달할 필요가 없습니다

**실제 서비스에서는**:
- 사용자가 브라우저에서 이 URL을 방문
- 로그인 폼이 표시됨 (THIRD_PARTY 클라이언트는 로그인 후 consent 화며이 추가 표시됨)
- 이메일/비밀번호 입력

---

### 3-3. Login (formLogin)

**Postman Request**: `3-3. Login (formLogin)`

**Method**: POST
**URL**: `{{base_url}}/oauth/login`
**Content-Type**: `application/x-www-form-urlencoded`
**Body**:
```
email=testuser@example.com
password=TestPassword123!
```

> OAuth 파라미터(client_id, state, code_challenge 등)는 **전송하지 않습니다**.
> 서버가 request cache에 저장해 둔 authorize 요청을 자동으로 재개합니다.

**Expected Response** (302 Redirect):
```
Location: http://localhost:8080/oauth/authorize?client_id=...&code_challenge=... (저장된 authorize 요청)
```

**Postman 설정**:
- Settings → "Automatically follow redirects" **끄기**
- Test 스크립트가 `resume_url` 변수에 Location을 저장

**로그인 실패 시**: `/oauth/login?error=credentials|disabled|unverified`로 리다이렉트됩니다.

---

### 3-3b. Resume Authorization (Authorization Code 획득)

**Postman Request**: `3-3b. Resume Authorization (get code)`

**Method**: GET
**URL**: `{{resume_url}}`

로그인으로 발급된 세션 쿠키(`HYFATA_SESSION`)가 Postman에 의해 자동 전송되고,
인증된 상태로 authorize가 재개되어 콜백으로 리다이렉트됩니다.

**Expected Response** (302 Redirect):
```
Location: http://localhost:3000/callback?code=AUTH_CODE_HERE&state=STATE_HERE
```

**실행 후 확인**:
- Headers 탭에서 `Location` 헤더 확인
- Variables에 `authorization_code` 저장 확인
- `state`가 3-1에서 생성한 값과 일치하는지 테스트 스크립트가 검증

---

### 3-4. Token Exchange (PKCE Verification)

**Postman Request**: `3-4. Token Exchange (with PKCE)`

**Method**: POST
**URL**: `{{base_url}}/oauth/token`
**Authorization**: Basic Auth (username=`{{client_id}}`, password=`{{client_secret}}`)
**Content-Type**: `application/x-www-form-urlencoded`
**Body**:
```
grant_type=authorization_code
code={{authorization_code}}
redirect_uri={{redirect_uri}}
code_verifier={{code_verifier}}
```

> **변경**: confidential 클라이언트는 `client_id`/`client_secret`을 본문이 아닌 **HTTP Basic 인증 헤더**로 전송합니다
> (SAS의 `client_secret_basic` 방식). Public 클라이언트는 Basic 인증 없이 `client_id` 본문 파라미터만 전송합니다.

**Expected Response** (200 OK):
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9... (RS256 JWT, 15분 유효)",
  "refresh_token": "k8s3hd... (opaque 문자열, 14일 유효)",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "profile email"
}
```

**중요**:
- `expires_in`은 **초 단위(900 = 15분)**입니다
- `code_verifier`가 3-1에서 생성한 값과 일치해야 함
- 서버는 `SHA256(code_verifier) == code_challenge`를 검증
- 검증 성공 시 Access Token(RS256 JWT) & Refresh Token(opaque) 발급 + 세션 생성

---

## Section 4️⃣: Token Usage - 토큰 사용

### 4-1. Refresh Access Token (OAuth 방식)

**Postman Request**: `4-1. OAuth Refresh Token`

**Method**: POST
**URL**: `{{base_url}}/oauth/token`
**Authorization**: Basic Auth (username=`{{client_id}}`, password=`{{client_secret}}`)
**Content-Type**: `application/x-www-form-urlencoded`
**Body**:
```
grant_type=refresh_token
refresh_token={{refresh_token}}
```

**Expected Response** (200 OK):
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9...(새 토큰)",
  "refresh_token": "m2j9kl...(새 Refresh Token)",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "profile email"
}
```

**중요**:
- **토큰 로테이션**: 갱신 시 새로운 Refresh Token도 발급됨 (기존 토큰 즉시 무효화)
- **Reuse detection**: 무효화된 Refresh Token을 재사용하면 해당 세션 전체가 즉시 무효화됨
- **세션 관리**: 자동으로 새 세션 생성, 기존 세션 무효화
- 갱신 후 반드시 새로운 `access_token`과 `refresh_token`으로 업데이트

**언제 사용하나?**
- Access Token이 만료되었을 때 (15분 후)
- 새로운 Access Token을 발급받을 수 있음

---

### 4-2. OAuth Logout (로그아웃)

**Postman Request**: `4-2. OAuth Logout`

**Method**: POST
**URL**: `{{base_url}}/oauth/logout`
**Headers**:
```
Authorization: Bearer {{access_token}}
Content-Type: application/json
```
**Body**:
```json
{
  "refresh_token": "{{refresh_token}}"
}
```

**Expected Response** (200 OK):
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

**중요**:
- **인증 필요**: 반드시 유효한 Access Token으로 인증 필요
- **세션 무효화**: 해당 세션이 즉시 무효화됨
- **토큰 블랙리스트**: Access Token의 JTI가 Redis 블랙리스트에 등록됨
- 로그아웃 후에는 해당 토큰으로 민감한 API 접근 불가

---

### 4-3. Protected Resource Access (선택사항)

보호된 리소스에 접근하려면:

**Example Request**:
```bash
GET /api/protected/your-endpoint
Authorization: Bearer {{access_token}}
```

**Postman에서**:
- Authorization 탭 → Type: Bearer Token
- Token: `{{access_token}}`

---

### 4-4. 세션 목록 조회

**Postman Request**: `4-4. Get Active Sessions`

**Method**: GET
**URL**: `{{base_url}}/api/sessions`
**Headers**:
```
Authorization: Bearer {{access_token}}
```

**Expected Response** (200 OK):
```json
{
  "totalSessions": 1,
  "sessions": [
    {
      "sessionId": "abc123def456...",
      "deviceType": "Desktop",
      "deviceName": "Chrome on Windows",
      "ipAddress": "192.168.1.100",
      "location": "Seoul, South Korea",
      "lastActiveAt": "2024-01-15T10:30:00",
      "createdAt": "2024-01-15T09:00:00",
      "expiresAt": "2024-01-29T09:00:00",
      "isCurrent": true
    }
  ]
}
```

---

### 4-5. 특정 세션 무효화 (원격 로그아웃)

**Postman Request**: `4-5. Revoke Session`

**Method**: DELETE
**URL**: `{{base_url}}/api/sessions/{sessionId}`
**Headers**:
```
Authorization: Bearer {{access_token}}
```

**Expected Response** (200 OK):
```json
{
  "message": "Session revoked successfully"
}
```

---

## Section 5️⃣: Error Tests - 에러 케이스

### 5-1. Invalid code_verifier

**Postman Request**: `5-1. Error: Invalid code_verifier`

Token Exchange를 잘못된 `code_verifier`로 시도:

**Expected** (400 Bad Request, SAS):
```json
{
  "error": "invalid_grant",
  "error_description": "..."
}
```

---

### 5-2. Missing code_verifier

**Postman Request**: `5-2. Error: Missing code_verifier`

Token Exchange에서 `code_verifier`를 빼고 시도:

**Expected** (400/401, SAS):
```json
{
  "error": "invalid_request",
  "error_description": "..."
}
```

---

### 5-3. Reuse Authorization Code

**Postman Request**: `5-3. Error: Reuse Authorization Code`

3-4에서 성공한 후, 같은 `authorization_code`로 다시 시도:

**Expected** (400 Bad Request, SAS):
```json
{
  "error": "invalid_grant",
  "error_description": "..."
}
```

**이유**: Authorization Code는 **일회성**이며, 재사용 시 SAS가 해당 authorization 전체를 무효화합니다.

---

## 🔄 전체 플로우 처음부터 다시 실행하기

### 새로운 사용자로 다시 테스트:
1. `1-1. User Registration` → 다른 이메일 사용
2. DB에서 이메일 검증
3. `3-1` → `3-2` → `3-3` → `3-4` 순서대로 실행

### 같은 사용자로 다시 로그인:
1. `3-1. Generate Code Challenge` (새로운 code_verifier 생성)
2. `3-2` → `3-3` → `3-4` 순서대로 실행
3. 새로운 Access Token 획득

---

## 🐛 트러블슈팅

### "Email already registered"
- 이미 가입된 이메일
- 다른 이메일 사용 또는 DB에서 해당 사용자 삭제

### "Email verification required"
- Section 2에서 이메일 검증을 건너뛰었을 가능성
- DB에서 `email_verified = true` 설정

### "Invalid client"
- Section 0에서 클라이언트 등록을 건너뛰었을 가능성
- `client_id`와 `client_secret` 확인

### "Invalid redirect URI"
- 클라이언트 등록 시 `redirectUris`와 정확히 일치해야 함
- `http://localhost:3000/callback` vs `http://localhost:3001/callback` 다름

### "PKCE verification failed"
- `code_verifier`가 올바르지 않음
- Section 3-1을 건너뛰었을 가능성
- 3-1 → 3-2 → 3-3 → 3-4 순서 준수 필수

### "Authorization code has already been used"
- Authorization Code는 일회성 (한 번만 사용 가능)
- 새로운 code를 얻으려면 3-3부터 다시 실행

---

## ✅ 체크리스트

### 시작 전:
- [ ] 애플리케이션 실행 (`./gradlew bootRun`)
- [ ] Redis 서버 실행 (세션 블랙리스트용)
- [ ] Postman Collection import
- [ ] DB 접근 가능 (이메일 검증용)

### 테스트 실행:
- [ ] Section 0: OAuth 클라이언트 등록
- [ ] Section 1: 회원가입
- [ ] Section 2: 이메일 검증 (DB)
- [ ] Section 3-1: Code Challenge 생성
- [ ] Section 3-2: Authorization Request (302 → 로그인)
- [ ] Section 3-3: Login (formLogin)
- [ ] Section 3-3b: Resume Authorization (Authorization Code 획득)
- [ ] Section 3-4: Token Exchange (Basic 인증, Access Token 획득 + 세션 생성)
- [ ] Section 4-1: OAuth Refresh Token 테스트
- [ ] Section 4-2: OAuth Logout 테스트
- [ ] Section 4-4: 세션 목록 조회
- [ ] Section 4-5: 원격 로그아웃 테스트
- [ ] Section 5: Error Tests

---

## 📚 참고사항

### PKCE란?
- **Proof Key for Code Exchange**
- Authorization Code 탈취 공격 방지
- 모바일/데스크톱 앱에 필수

### 레거시 로그인 엔드포인트 (삭제됨)

`POST /api/auth/login`, `POST /api/auth/refresh`는 **삭제**되었습니다.
모든 로그인/토큰 갱신은 OAuth 2.0 + PKCE 흐름(SAS)을 사용하세요.

------|-------------------------------|------------------|
| 사용처 | 직접 인증 (자체 앱) | 제3자 앱, 모바일 앱 |
| 보안 | JWT만 사용 | PKCE + State + Client Secret |
| 토큰 | Access + Refresh | Access + Refresh |
| 권장 | ❌ Deprecated | ✅ 권장 |

---

**이제 실제 서비스 흐름과 동일하게 회원가입부터 OAuth 로그인까지 모든 것을 테스트할 수 있습니다!** 🎯
