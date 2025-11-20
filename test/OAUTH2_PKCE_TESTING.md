# OAuth 2.0 + PKCE (Proof Key for Code Exchange) 테스트 가이드

Hyfata REST API의 OAuth 2.0 Authorization Code Flow with PKCE를 Postman으로 테스트하는 완전한 가이드입니다.

---

## 📌 개요

**OAuth 2.0 Authorization Code Flow with PKCE**는 모바일/데스크톱 앱에서 사용자를 안전하게 인증하기 위한 표준 프로토콜입니다.

- **3단계 프로세스**: Authorization → Login → Token Exchange
- **PKCE**: Code Verifier/Challenge로 코드 탈취 방지
- **보안**: State 파라미터로 CSRF 공격 방지, 클라이언트 시크릿으로 백엔드 검증

---

## 🔧 사전 준비 (필수)

### 1. 애플리케이션 실행
```bash
cd /Users/najoan/IdeaProjects/Hyfata-RestAPI
./gradlew bootRun
```

### 2. 데이터베이스에 테스트 사용자 생성
```sql
-- 사용자 생성 및 이메일 검증
INSERT INTO users (email, username, password, first_name, last_name, email_verified, enabled, created_at)
VALUES (
  'oauth-test@example.com',
  'oauthuser',
  '$2a$10$slYQmyNdGzin7olVN3p5Be7DFYo7DeNsmz9c12HDGa8/DyKV8UXZO',  -- BCrypt hash of 'TestPassword123!'
  'OAuth',
  'Test',
  true,
  true,
  NOW()
);

-- OAuth 클라이언트 등록
INSERT INTO oauth_clients (client_id, client_secret, redirect_uris, scope, created_at)
VALUES (
  'test-client-001',
  'test-secret-001',
  'http://localhost:3000/callback,http://localhost:3001/callback',
  'user:email user:profile',
  NOW()
);
```

### 3. Postman 준비
1. Postman 설치: https://www.postman.com/downloads/
2. 본 디렉토리의 `OAuth2_PKCE_Postman_Collection.json` import
3. 환경 변수 설정 (또는 컬렉션 변수 사용)

---

## 🔄 OAuth 2.0 + PKCE 플로우

```
┌──────────────┐                          ┌─────────────────┐
│   Client     │                          │  Authorization  │
│  (Postman)   │                          │    Server       │
└──────┬───────┘                          └────────┬────────┘
       │                                           │
       │ 1️⃣ Generate code_verifier & code_challenge
       │    (Pre-request script)
       │
       │ 2️⃣ GET /oauth/authorize?
       │    code_challenge=...&client_id=...
       ├──────────────────────────────────────────→
       │
       │ 3️⃣ 로그인 페이지 표시
       │←──────────────────────────────────────────┤
       │
       │ 4️⃣ POST /oauth/login
       │    (email, password, code_challenge)
       ├──────────────────────────────────────────→
       │
       │ 5️⃣ Authorization Code 생성
       │    Redirect: callback?code=...&state=...
       │←──────────────────────────────────────────┤
       │
       │ 6️⃣ POST /oauth/token
       │    (code, code_verifier, client_secret)
       ├──────────────────────────────────────────→
       │
       │ 7️⃣ Access Token & Refresh Token 반환
       │←──────────────────────────────────────────┤
```

---

## 🧪 Postman에서 단계별 테스트

### ✅ 사전 확인
Postman Collection 변수 확인:
- `base_url`: `http://localhost:8080`
- `client_id`: `test-client-001`
- `client_secret`: `test-secret-001`
- `redirect_uri`: `http://localhost:3000/callback`
- `email`: `oauth-test@example.com`
- `password`: `TestPassword123!`

### Step 1️⃣: Code Challenge 생성
**요청명**: `1. Generate Code Challenge`
- **방식**: Pre-request Script (실제 HTTP 요청 없음)
- **역할**:
  - code_verifier 생성 (128자 무작위 문자열)
  - code_challenge 생성 (SHA-256 해시 + Base64 URL 인코딩)
  - 환경 변수에 저장

**Postman에서 확인**:
- Console을 열어서 생성된 값 확인
- 변수 탭에서 `code_verifier`, `code_challenge`, `state` 확인

---

### Step 2️⃣: Authorization 요청
**요청명**: `2. Authorization Request (with PKCE)`
- **메서드**: GET
- **URL**: `{{base_url}}/oauth/authorize`
- **파라미터**:
  ```
  client_id=test-client-001
  redirect_uri=http://localhost:3000/callback
  response_type=code
  state={{state}}
  code_challenge={{code_challenge}}
  code_challenge_method=S256
  ```

**예상 응답**:
- **상태**: 200 OK
- **응답**: 로그인 페이지 (HTML)
- **내용**: 숨겨진 필드에 client_id, redirect_uri, state 포함

**Postman에서 실행**:
1. "Send" 클릭
2. 응답의 "Preview" 탭에서 로그인 폼 확인
3. 폼에 code_challenge, code_challenge_method가 숨겨진 필드로 있는지 확인

---

### Step 3️⃣: 로그인 및 Authorization Code 획득
**요청명**: `3. Login & Get Authorization Code`
- **메서드**: POST
- **URL**: `{{base_url}}/oauth/login`
- **Content-Type**: `application/x-www-form-urlencoded`
- **Body**:
  ```
  email=oauth-test@example.com
  password=TestPassword123!
  client_id=test-client-001
  redirect_uri=http://localhost:3000/callback
  state={{state}}
  code_challenge={{code_challenge}}
  code_challenge_method=S256
  ```

**예상 응답**:
- **상태**: 302 Redirect (또는 브라우저는 자동 리다이렉트)
- **Header**: `Location: http://localhost:3000/callback?code=...&state=...`

**Postman에서 실행**:
1. Settings → "Follow redirects" 끄기 (Location 헤더 확인용)
2. "Send" 클릭
3. "Headers" 탭에서 `Location` 헤더 확인
4. URL에서 `code` 값 복사
5. Test 스크립트가 자동으로 `authorization_code` 변수에 저장함

**주의**: Authorization Code는 **일회성**입니다 (다음 단계에서만 사용 가능)

---

### Step 4️⃣: Token Exchange (PKCE Verification)
**요청명**: `4. Token Exchange (with PKCE Verification)`
- **메서드**: POST
- **URL**: `{{base_url}}/oauth/token`
- **Content-Type**: `application/x-www-form-urlencoded`
- **Body**:
  ```
  grant_type=authorization_code
  code={{authorization_code}}
  client_id=test-client-001
  client_secret=test-secret-001
  redirect_uri=http://localhost:3000/callback
  code_verifier={{code_verifier}}
  ```

**예상 응답** (200 OK):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 86400000,
  "scope": "user:email user:profile"
}
```

**Postman에서 실행**:
1. "Send" 클릭
2. Response의 "Tests" 탭에서 자동 검증 결과 확인
3. access_token과 refresh_token이 변수에 저장됨
4. Console에서 성공 메시지 확인

---

## 🔴 에러 케이스 테스트

### Error Test 1: 잘못된 code_verifier
**요청명**: `5. Test: Invalid code_verifier`
- code_verifier를 잘못된 값으로 변경
- Token Exchange 요청 전송

**예상**: 400 Bad Request
```json
{
  "error": "invalid_grant",
  "error_description": "PKCE verification failed: code_verifier does not match code_challenge"
}
```

---

### Error Test 2: code_verifier 누락
**요청명**: `6. Test: Missing code_verifier`
- Body에서 code_verifier 제거
- Token Exchange 요청 전송

**예상**: 400 Bad Request
```json
{
  "error": "invalid_grant",
  "error_description": "code_verifier is required (code_challenge was provided)"
}
```

---

### Error Test 3: 잘못된 client_secret
**요청명**: `7. Test: Invalid client_secret` (수동 작성 필요)
- client_secret을 잘못된 값으로 변경
- Token Exchange 요청 전송

**예상**: 400 Bad Request
```json
{
  "error": "invalid_client",
  "error_description": "Client authentication failed"
}
```

---

### Error Test 4: Authorization Code 재사용
**요청명**: `8. Test: Reuse Authorization Code` (수동 작성 필요)
- Step 4에서 성공한 후, 같은 code로 다시 Token Exchange 시도

**예상**: 400 Bad Request
```json
{
  "error": "invalid_grant",
  "error_description": "Authorization code has already been used"
}
```

---

## 🔄 전체 플로우 실행 (처음부터 끝까지)

### Postman에서 순서대로 실행:
1. ✅ `1. Generate Code Challenge` 실행
   - Pre-request script 동작 확인
   - Console에서 code_verifier, code_challenge 확인

2. ✅ `2. Authorization Request (with PKCE)` 실행
   - 로그인 페이지 HTML 응답 확인
   - code_challenge가 폼에 포함되어 있는지 확인

3. ✅ `3. Login & Get Authorization Code` 실행
   - Location 헤더에서 authorization_code 추출
   - Test 스크립트가 authorization_code 변수 저장

4. ✅ `4. Token Exchange (with PKCE Verification)` 실행
   - access_token과 refresh_token 획득
   - Test 스크립트가 검증 완료

5. ✅ 완료! 이제 access_token으로 보호된 리소스 접근 가능

---

## 📝 새 Authorization Code 생성 (반복 테스트용)

전체 플로우를 다시 실행하려면:

1. Step 1부터 다시 실행하면 새로운 code_verifier와 code_challenge가 생성됨
2. Step 2 → Step 3 → Step 4 순서대로 실행
3. 새로운 access_token 획득

**주의**: 같은 authorization_code는 재사용 불가능합니다. 새로 생성해야 합니다.

---

## 🐛 트러블슈팅

### "Invalid redirect URI"
- client_id의 등록된 redirect_uri 확인
- URL이 정확히 일치하는지 확인 (프로토콜, 도메인, 경로 모두)
- `http://localhost:3000/callback` vs `http://localhost:3001/callback` 다름

### "Invalid client"
- client_id, client_secret이 정확한지 확인
- 데이터베이스에서 oauth_clients 테이블 확인
- 동일한 값을 사용하고 있는지 확인

### "PKCE verification failed"
- code_verifier가 올바른지 확인
- Step 1을 건너뛰었을 수 있음
- Step 1 → Step 2 → Step 3 → Step 4 순서 준수

### "Authorization code has already been used"
- authorization_code는 일회성 (한 번만 사용 가능)
- 새로운 authorization_code를 얻으려면 Step 3부터 다시 실행

### "Email verification required"
- 테스트 사용자의 email_verified 컬럼이 true인지 확인
- SQL: `UPDATE users SET email_verified = true WHERE email = 'oauth-test@example.com';`

---

## 📚 참고 사항

### PKCE (RFC 7636) 란?
- **목적**: Authorization Code Interception Attacks 방지
- **특히 유용**: 모바일 앱, 데스크톱 앱 (기본 인증 불가능한 환경)
- **동작**:
  1. Client: code_verifier 생성 (128자 무작위 문자열)
  2. Client: code_challenge = Base64URL(SHA256(code_verifier))
  3. Authorization 요청에 code_challenge 전송
  4. Authorization Server: code_challenge 저장
  5. Token 요청에 code_verifier 전송
  6. Authorization Server: SHA256(code_verifier) == code_challenge 검증
  7. 일치하면 token 발급, 불일치하면 거부

### Access Token 사용
Token Exchange 성공 후:
```bash
curl -H "Authorization: Bearer {{access_token}}" \
     http://localhost:8080/api/protected/your-endpoint
```

### Refresh Token 사용
Access Token이 만료되면:
```bash
POST /oauth/token
grant_type=refresh_token
refresh_token={{refresh_token}}
client_id=test-client-001
client_secret=test-secret-001
```

---

## ✅ 체크리스트

테스트 시작 전 확인:
- [ ] 애플리케이션 실행 (`./gradlew bootRun`)
- [ ] 테스트 사용자 생성 (oauth-test@example.com)
- [ ] 이메일 검증 완료
- [ ] OAuth 클라이언트 등록 (test-client-001)
- [ ] Postman Collection import
- [ ] 환경 변수 설정 또는 컬렉션 변수 업데이트

테스트 실행:
- [ ] Step 1-4 순서대로 실행
- [ ] 각 단계에서 예상 응답 확인
- [ ] Error Test 실행 (5-8)
- [ ] Console에서 에러 메시지 확인

---

끝!
