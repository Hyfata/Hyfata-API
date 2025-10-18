# OAuth 2.0 Authorization Code Flow - 상세 가이드

## 개요

Hyfata REST API는 이제 **OAuth 2.0 Authorization Code Flow**를 구현하여 Google, Discord, GitHub OAuth와 동일한 보안 표준을 따릅니다.

### 핵심 특징
✅ Authorization Code로 인증 정보 보호
✅ Client Secret 노출 방지
✅ State 파라미터로 CSRF 공격 방지
✅ 토큰 한 번 사용 원칙 준수
✅ 만료된 코드 자동 정리

---

## 1. Authorization Code Flow 단계

```
┌─────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│  Site 1         │         │  Hyfata API      │         │  User Browser    │
│  (Frontend)     │         │  (OAuth Server)  │         │                  │
└─────────────────┘         └──────────────────┘         └──────────────────┘
         │                           │                           │
    1    │  사용자가 "로그인" 클릭    │                           │
         │──────────────────────────────────────────────────────>│
    2    │                           │  /oauth/authorize로 리다이렉트
         │<──────────────────────────────────────────────────────│
    3    │                           │
         │                    로그인 페이지 표시
         │                           │
    4    │                           │  사용자가 이메일/비밀번호 입력
         │                           │<──────────────────────────┤
    5    │                           │  /oauth/login로 제출
         │                           │  (비밀번호 검증, Authorization Code 생성)
         │                           │
    6    │  Authorization Code 반환   │
         │<──────────────────────────┤
         │  code=xxx&state=xxx       │
    7    │  (Site 1 백엔드)           │
         │                           │
    8    │  /oauth/token 호출        │
         │  (clientSecret 포함)      │
         │  ──────────────────────────>
         │                           │
    9    │  Access Token 발급        │
         │<──────────────────────────┤
    10   │  JWT 토큰을 쿠키에 저장   │
         │                           │
```

---

## 2. 상세 구현 단계

### 단계 1: 클라이언트 등록

사이트가 Hyfata API를 사용하려면 먼저 클라이언트로 등록해야 합니다.

```bash
curl -X POST http://localhost:8080/api/clients/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Web App",
    "description": "My awesome web application",
    "frontendUrl": "https://myapp.com",
    "redirectUris": [
      "https://myapp.com/callback",
      "https://myapp.com/auth/callback"
    ],
    "maxTokensPerUser": 5
  }'
```

**응답:**
```json
{
  "message": "Client registered successfully",
  "client": {
    "clientId": "client_1697406234567_4829",
    "clientSecret": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
    "name": "My Web App",
    "frontendUrl": "https://myapp.com",
    "redirectUris": ["https://myapp.com/callback"],
    ...
  }
}
```

**중요**: `clientSecret`을 안전하게 보관하세요!

---

### 단계 2: Authorization Request (1단계)

사용자가 "로그인" 버튼을 클릭하면, 프론트엔드는 사용자를 Hyfata API의 로그인 페이지로 리다이렉트합니다.

**URL:**
```
GET /oauth/authorize?
  client_id=client_1697406234567_4829&
  redirect_uri=https://myapp.com/callback&
  state=random_state_value_12345&
  response_type=code
```

**파라미터:**
| 파라미터 | 필수 | 설명 |
|---------|------|------|
| `client_id` | ✅ | 클라이언트 ID |
| `redirect_uri` | ✅ | 로그인 후 리다이렉트할 URL (등록된 URI 중 하나) |
| `state` | ✅ | CSRF 방지용 임의의 값 (상태 유지용) |
| `response_type` | ✅ | 항상 "code" (고정값) |

**처리:**
- Hyfata API가 파라미터 검증
- 로그인 페이지 (`/oauth/login`) 표시

---

### 단계 3: 사용자 로그인 (2단계)

사용자가 이메일과 비밀번호를 입력하고 "Sign In" 클릭.

**요청:**
```
POST /oauth/login

이메일: user@example.com
비밀번호: password123
client_id: client_1697406234567_4829
redirect_uri: https://myapp.com/callback
state: random_state_value_12345
```

**Hyfata API 처리:**
1. ✅ 사용자 조회
2. ✅ 비밀번호 검증 (BCrypt)
3. ✅ 계정 활성화 확인
4. ✅ 이메일 검증 확인
5. ✅ Authorization Code 생성 (10분 유효)
6. ✅ 사용자를 redirect_uri로 리다이렉트

**리다이렉트:**
```
https://myapp.com/callback?
  code=authorization_code_value_12345&
  state=random_state_value_12345
```

**중요:**
- Authorization Code는 **한 번만 사용 가능**
- **10분 후 만료**됨
- State 값이 일치해야 함 (CSRF 방지)

---

### 단계 4: Code를 Token으로 교환 (3단계)

Site 1 백엔드는 받은 authorization code를 accessToken으로 교환합니다.

**요청 (Site 1 백엔드 → Hyfata API):**
```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&
      code=authorization_code_value_12345&
      client_id=client_1697406234567_4829&
      client_secret=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6&
      redirect_uri=https://myapp.com/callback"
```

**파라미터:**
| 파라미터 | 필수 | 설명 |
|---------|------|------|
| `grant_type` | ✅ | 항상 "authorization_code" |
| `code` | ✅ | 단계 3에서 받은 Authorization Code |
| `client_id` | ✅ | 클라이언트 ID |
| `client_secret` | ✅ | 클라이언트 Secret (⚠️ 백엔드에서만 사용!) |
| `redirect_uri` | ✅ | 초기 요청과 동일한 redirect_uri |

**응답 (성공):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 86400000,
  "scope": "user:email user:profile"
}
```

**응답 (실패):**
```json
{
  "error": "invalid_grant",
  "error_description": "Authorization code expired"
}
```

**중요:**
- `client_secret`은 **절대 프론트엔드에 노출하면 안 됨**
- Site 1의 **백엔드 서버에서만** 이 요청을 해야 함
- Authorization Code는 이 요청 후 **사용 불가능**으로 표시됨

---

### 단계 5: 토큰 저장 및 사용 (4단계)

Site 1 백엔드는 받은 토큰을 사용자 세션에 저장합니다.

```javascript
// Site 1 백엔드
app.get('/callback', async (req, res) => {
  const { code, state } = req.query;

  // State 검증 (CSRF 방지)
  if (state !== req.session.oauthState) {
    return res.status(400).send('Invalid state parameter');
  }

  // Authorization Code를 토큰으로 교환
  const response = await fetch('http://localhost:8080/oauth/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code: code,
      client_id: 'client_1697406234567_4829',
      client_secret: 'a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6',
      redirect_uri: 'https://myapp.com/callback',
    }),
  });

  const tokens = await response.json();

  // HttpOnly 쿠키에 토큰 저장 (XSS 방지)
  res.cookie('accessToken', tokens.access_token, {
    httpOnly: true,
    secure: true,
    sameSite: 'Strict',
    maxAge: 24 * 60 * 60 * 1000, // 24시간
  });

  res.redirect('/dashboard');
});
```

---

## 3. 보안 메커니즘

### 3.1 State 파라미터 (CSRF 방지)

**목적:** 공격자가 임의로 리다이렉트되는 것을 방지

**동작:**
```
1. Site 1 프론트엔드 → state 생성 후 세션에 저장
   state = "random_uuid_123"

2. Site 1 프론트엔드 → /oauth/authorize?state=random_uuid_123

3. 사용자 로그인 후, Hyfata API → code와 state 반환
   /callback?code=xxx&state=random_uuid_123

4. Site 1 백엔드 → state 검증
   if (state !== session.storedState) {
     throw new Error("Invalid state - CSRF attack detected!");
   }
```

### 3.2 Authorization Code의 일회용 원칙

**목적:** Authorization Code를 탈취해도 재사용 불가능하게 함

**동작:**
```
1. 사용자 로그인 성공
   → Authorization Code 생성: used = false

2. Site 1 백엔드가 code를 token으로 교환
   → used = true로 표시

3. 공격자가 같은 code 재사용 시도
   → "Authorization code already used" 에러
```

### 3.3 Client Secret 보호

**목적:** 클라이언트 인증 정보 보호

**동작:**
```
❌ 안 됨: 프론트엔드에서 client_secret 포함
GET /oauth/authorize?client_id=xxx&client_secret=yyy
→ 브라우저 히스토리/로그에 노출!

✅ 맞음: 백엔드에서 client_secret 포함
POST /oauth/token (HTTPS를 통한 안전한 채널)
Body: client_secret=yyy
```

### 3.4 토큰 만료 및 정리

**목적:** 만료된 인증 코드 정리

**설정:**
```java
// Authorization Code: 10분 유효
expiresAt = LocalDateTime.now().plusMinutes(10);

// Access Token: 24시간 유효
// Refresh Token: 7일 유효
```

---

## 4. 실제 사용 예시

### 예시 1: Site 1에서 사용자 로그인

#### 1단계: 프론트엔드 (Site 1)
```html
<button onclick="loginWithOAuth()">
  Sign in with Hyfata
</button>

<script>
function loginWithOAuth() {
  // State 생성 및 저장
  const state = generateRandomState();
  sessionStorage.setItem('oauthState', state);

  // Authorization URL 구성
  const params = new URLSearchParams({
    client_id: 'client_1697406234567_4829',
    redirect_uri: 'https://myapp.com/callback',
    state: state,
    response_type: 'code',
  });

  // Hyfata 로그인 페이지로 리다이렉트
  window.location.href = `http://localhost:8080/oauth/authorize?${params}`;
}
</script>
```

#### 2단계: 백엔드 (Site 1)
```javascript
// Callback 처리
app.get('/callback', async (req, res) => {
  const { code, state } = req.query;

  // 1. State 검증
  const storedState = req.session.oauthState;
  if (state !== storedState) {
    return res.status(400).send('CSRF attack detected');
  }

  try {
    // 2. Code를 Token으로 교환
    const tokenResponse = await fetch('http://localhost:8080/oauth/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        code: code,
        client_id: 'client_1697406234567_4829',
        client_secret: process.env.OAUTH_CLIENT_SECRET,
        redirect_uri: 'https://myapp.com/callback',
      }),
    });

    const tokens = await tokenResponse.json();

    if (tokens.error) {
      return res.status(400).send('Token exchange failed: ' + tokens.error_description);
    }

    // 3. 토큰을 쿠키에 저장 (HttpOnly)
    res.cookie('accessToken', tokens.access_token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'Strict',
      maxAge: 24 * 60 * 60 * 1000,
    });

    // 4. 대시보드로 리다이렉트
    res.redirect('/dashboard');
  } catch (error) {
    res.status(500).send('Login failed: ' + error.message);
  }
});
```

#### 3단계: API 요청 (Site 1 프론트엔드)
```javascript
// 대시보드에서 인증된 요청
async function fetchUserProfile() {
  const response = await fetch('/api/user/profile', {
    method: 'GET',
    credentials: 'include', // 쿠키 포함
  });

  const userData = await response.json();
  console.log(userData);
}
```

---

## 5. 에러 처리

### 일반적인 에러

| 에러 | 원인 | 해결책 |
|-----|------|--------|
| `invalid_client` | 잘못된 client_id | client_id 확인 |
| `invalid_redirect_uri` | 등록되지 않은 redirect_uri | 클라이언트 설정에서 URI 등록 |
| `invalid_grant` | 만료된 또는 사용된 code | code 재사용 불가 |
| `invalid_scope` | 지원하지 않는 scope | scope 확인 |
| `server_error` | 서버 에러 | 로그 확인 |

### 예시: 에러 응답
```json
{
  "error": "invalid_grant",
  "error_description": "Authorization code expired"
}
```

---

## 6. 보안 체크리스트

### ✅ 구현해야 할 것

- [x] Authorization Code 한 번 사용 원칙
- [x] State 파라미터 CSRF 방지
- [x] Client Secret 서버에서만 사용
- [x] HTTPS 사용 (프로덕션)
- [ ] Redirect URI 화이트리스트
- [ ] Code 만료 (10분)
- [ ] 로그인 시도 제한 (Rate Limiting)
- [ ] 의심 활동 감지

### ⚠️ 주의사항

- ❌ Client Secret을 프론트엔드에 노출하지 마세요
- ❌ Authorization Code를 URL에 노출하지 마세요 (POST 사용)
- ❌ 토큰을 localStorage에 저장하지 마세요 (HttpOnly 쿠키 사용)
- ❌ State 파라미터를 무시하지 마세요
- ❌ HTTPS를 무시하지 마세요 (프로덕션)

---

## 7. 레거시 API (더 이상 권장하지 않음)

### ⚠️ DEPRECATED

이 API들은 여전히 작동하지만 OAuth 2.0 대신 사용하지 마세요:

```bash
# 권장하지 않음
POST /api/auth/register
POST /api/auth/login

# 대신 사용하세요
GET /oauth/authorize
POST /oauth/login
POST /oauth/token
```

---

## 8. 자주 묻는 질문 (FAQ)

### Q: Authorization Code의 유효 기간은?
**A:** 10분입니다. 이 시간 내에 token으로 교환해야 합니다.

### Q: Refresh Token을 사용하려면?
**A:** Access Token이 만료되었을 때:
```bash
POST /api/auth/refresh
{
  "refreshToken": "..."
}
```

### Q: State 파라미터를 생략할 수 있나요?
**A:** 권장하지 않습니다. CSRF 공격에 취약해집니다. 항상 사용하세요.

### Q: 클라이언트를 여러 개 등록할 수 있나요?
**A:** 예. 각 사이트/앱마다 별도의 clientId와 clientSecret을 생성하세요.

### Q: 사용자가 두 사이트에 모두 가입하면?
**A:** 동일한 이메일로 두 사이트에 가입 가능합니다. 두 사이트 모두 동일한 JWT 토큰을 받습니다.

---

## 9. 참고 자료

- [OAuth 2.0 공식 스펙](https://tools.ietf.org/html/rfc6749)
- [OIDC 보안 Best Practices](https://openid.net/specs/openid-connect-core-1_0.html)
- [Google OAuth 2.0 구현](https://developers.google.com/identity/protocols/oauth2)
