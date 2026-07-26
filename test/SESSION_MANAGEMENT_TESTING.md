# Session Management Testing Guide

이 가이드는 Postman을 사용하여 세션 관리 API를 테스트하는 방법을 설명합니다.

> **SAS 마이그레이션 이후**: 레거시 `POST /api/auth/login`, `POST /api/auth/refresh`는 삭제되었습니다.
> 토큰 발급/갱신은 OAuth 2.0 + PKCE 흐름(Spring Authorization Server)을 사용합니다.
> `Session_Management_Postman_Collection.json`도 현재 스펙 기준으로 갱신되었습니다.

## Prerequisites

### 1. Redis 서버 실행
세션 블랙리스트와 OAuth 로그인 세션 저장을 위해 Redis가 필요합니다.

```bash
# Docker를 사용하는 경우
docker run -d --name redis -p 6379:6379 redis:latest

# 또는 로컬 Redis 사용
redis-server
```

### 2. 환경 변수 설정 (.env)
```properties
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 3. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 4. 토큰 발급 (OAuth 2.0 + PKCE)

세션 API를 테스트하려면 먼저 Access Token이 필요합니다.
`OAuth2_PKCE_Complete_Testing` 컬렉션(또는 `OAUTH2_PKCE_TESTING.md` 가이드)의 Section 0~3을 실행하여
토큰을 발급받으세요. 요약:

1. OAuth 클라이언트 등록 (`POST /api/clients/register`) — `allowedScopes`에 `sessions:manage` 포함 필요
2. PKCE authorize → 로그인 → `/oauth/token` 교환
3. 발급된 `access_token` / `refresh_token`을 컬렉션 변수에 저장

> 세션 API는 **`sessions:manage` scope**가 필요합니다. authorize 요청 시
> `scope=profile email sessions:manage`를 포함하세요.

---

## Postman Environment Setup

### 변수 설정
Postman Environment(또는 Collection Variables)에 다음 변수를 추가하세요:

| Variable | Initial Value | Description |
|----------|---------------|-------------|
| `baseUrl` | `http://localhost:8080` | API 서버 주소 |
| `client_id` | (OAuth 클라이언트 ID) | 토큰 갱신 시 Basic 인증용 |
| `client_secret` | (OAuth 클라이언트 시크릿) | 토큰 갱신 시 Basic 인증용 (public 클라이언트는 불필요) |
| `accessToken` | (빈 값) | OAuth 토큰 발급 후 설정 |
| `refreshToken` | (빈 값) | OAuth 토큰 발급 후 설정 |

---

## API Endpoints

### 1. 회원가입

**POST** `{{baseUrl}}/api/auth/register`

```json
{
    "email": "test@example.com",
    "password": "Password123!",
    "username": "testuser"
}
```

**Response (201 Created):**
```json
{
    "message": "회원가입이 완료되었습니다. 이메일을 확인하여 계정을 인증해 주세요."
}
```

---

### 2. 토큰 갱신 (SAS)

**POST** `{{baseUrl}}/oauth/token`

**Authorization:** Basic Auth (username=`{{client_id}}`, password=`{{client_secret}}`)

**Body** (application/x-www-form-urlencoded):
```
grant_type=refresh_token
refresh_token={{refreshToken}}
```

**Response (200 OK):**
```json
{
    "access_token": "eyJhbGciOiJSUzI1NiJ9...",
    "refresh_token": "opaque...",
    "token_type": "Bearer",
    "expires_in": 900,
    "scope": "profile email sessions:manage"
}
```

- `expires_in`은 **초 단위(900 = 15분)**입니다.
- **토큰 로테이션**: 새 Refresh Token이 발급되고 기존 것은 즉시 무효화됩니다.
- **Reuse detection**: 무효화된 Refresh Token을 재사용하면 세션 전체가 무효화됩니다.

**Post-request Script (토큰 자동 저장):**
```javascript
if (pm.response.code === 200) {
    const response = pm.response.json();
    pm.collectionVariables.set("accessToken", response.access_token);
    pm.collectionVariables.set("refreshToken", response.refresh_token);
}
```

---

### 3. 활성 세션 목록 조회

현재 로그인된 모든 기기/세션을 조회합니다.

**GET** `{{baseUrl}}/api/sessions`

**Headers:**
```
Authorization: Bearer {{accessToken}}
X-Refresh-Token: {{refreshToken}}
```

`X-Refresh-Token` 헤더를 본낼 경우 해당 세션이 `isCurrent: true`로 표시됩니다.

**Response (200 OK):**
```json
{
    "totalSessions": 2,
    "sessions": [
        {
            "sessionId": "abc123def456...(sha256 해시)",
            "deviceType": "Desktop",
            "deviceName": "Chrome on Windows",
            "ipAddress": "192.168.1.100",
            "location": "Seoul, South Korea",
            "lastActiveAt": "2024-01-15T10:30:00",
            "createdAt": "2024-01-15T09:00:00",
            "expiresAt": "2024-01-29T09:00:00",
            "isCurrent": true
        },
        {
            "sessionId": "xyz789...",
            "deviceType": "Mobile",
            "deviceName": "Safari on iPhone",
            "ipAddress": "192.168.1.105",
            "location": "Seoul, South Korea",
            "lastActiveAt": "2024-01-15T08:00:00",
            "createdAt": "2024-01-14T15:00:00",
            "expiresAt": "2024-01-28T15:00:00",
            "isCurrent": false
        }
    ]
}
```

---

### 4. 특정 세션 무효화 (원격 로그아웃)

다른 기기의 세션을 원격으로 종료합니다.
세션 무효화 + Access Token JTI 블랙리스트 + SAS authorization(Refresh Token) 제거가 함께 수행됩니다.

**DELETE** `{{baseUrl}}/api/sessions/{sessionId}`

**Headers:**
```
Authorization: Bearer {{accessToken}}
```

**Path Variables:**
- `sessionId`: 무효화할 세션 ID (세션 목록에서 확인)

**Response (200 OK):**
```json
{
    "message": "Session revoked successfully"
}
```

---

### 5. 현재 세션 외 모든 세션 무효화

현재 사용 중인 세션을 제외한 모든 세션을 종료합니다.

**POST** `{{baseUrl}}/api/sessions/revoke-others`

**Headers:**
```
Authorization: Bearer {{accessToken}}
X-Refresh-Token: {{refreshToken}}
```

`X-Refresh-Token` 헤더(필수)로 현재 세션을 식별합니다.

**Response (200 OK):**
```json
{
    "message": "Other sessions revoked successfully"
}
```

---

### 6. 모든 세션 무효화 (전체 로그아웃)

**POST** `{{baseUrl}}/api/sessions/revoke-all`

**Headers:**
```
Authorization: Bearer {{accessToken}}
```

모든 세션 + 서버사이드 로그인 세션(Redis)도 함께 무효화됩니다.

**Response (200 OK):**
```json
{
    "message": "All sessions revoked successfully"
}
```

---

### 7. 로그아웃

**POST** `{{baseUrl}}/api/auth/logout`

**Headers:**
```
Authorization: Bearer {{accessToken}}
```

**Request Body:**
```json
{
    "refreshToken": "{{refreshToken}}",
    "logoutAll": false
}
```

**Response (200 OK):**
```json
{
    "message": "로그아웃되었습니다."
}
```

> `POST /oauth/logout`도 사용할 수 있습니다 (동일하게 세션 + JTI 블랙리스트 + SAS authorization 제거,
> 추가로 서버사이드 로그인 세션 무효화).

---

## Test Scenarios

### Scenario 1: 기본 세션 관리 흐름

1. **회원가입** - 새 계정 생성 + 이메일 검증
2. **OAuth 토큰 발급** - PKCE 흐름으로 Access/Refresh 토큰 획득
3. **세션 목록 조회** - 현재 세션 확인
4. **로그아웃** - 세션 종료

### Scenario 2: 다중 기기 로그인 테스트

1. **Device 1에서 로그인** (Chrome) - OAuth 흐름 1회
2. **Device 2에서 로그인** - OAuth 흐름을 다른 User-Agent로 1회 더
   - Headers에 추가: `User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)`
3. **세션 목록 조회** - 2개 세션 확인
4. **특정 세션 무효화** - Device 1 세션 종료
5. **세션 목록 조회** - 1개 세션만 남음 확인

### Scenario 3: 동시 세션 제한 테스트 (최대 5개)

1. 5개의 서로 다른 User-Agent로 로그인 (OAuth 흐름 5회)
2. 6번째 로그인
3. 가장 오래된 세션이 자동으로 무효화되는지 확인
4. 세션 목록에서 최대 5개만 있는지 확인

### Scenario 4: 토큰 블랙리스트 테스트

1. **OAuth 토큰 발급**
2. **로그아웃** (세션 무효화)
3. **민감한 API 호출** (`/api/sessions`)
4. **결과 확인**: `401 Unauthorized` + `"Token has been revoked"`

### Scenario 5: 토큰 갱신 흐름

1. **OAuth 토큰 발급**
2. **15분 대기** (또는 만료된 토큰 사용)
3. **`/oauth/token` (refresh_token grant)으로 갱신**
4. **새 토큰으로 API 호출**
5. (참고) 이전 Refresh Token으로 다시 갱신 시도 → 세션 전체 무효화 확인 (reuse detection)

---

## User-Agent Examples for Multi-Device Testing

다양한 기기를 시뮬레이션하려면 Postman Headers에서 `User-Agent`를 변경하세요:

### Desktop Browsers
```
# Chrome on Windows
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36

# Safari on macOS
Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Safari/605.1.15

# Firefox on Linux
Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0
```

### Mobile Devices
```
# Safari on iPhone
Mozilla/5.0 (iPhone; CPU iPhone OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Mobile/15E148 Safari/604.1

# Chrome on Android
Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36
```

### Tablets
```
# Safari on iPad
Mozilla/5.0 (iPad; CPU OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Mobile/15E148 Safari/604.1
```

---

## Error Responses

### 401 Unauthorized
```json
{
    "error": "Token has been revoked"
}
```
- 원인: 블랙리스트에 등록된 토큰으로 민감한 API 접근 시도
- 해결: 다시 로그인하여 새 토큰 획득

### 401 Unauthorized (만료/무효 토큰)
- 원인: Access Token 만료(15분) 또는 서명 검증 실패
- 해결: `/oauth/token` (refresh_token grant)으로 갱신

### 403 Forbidden
```json
{
    "error": "Insufficient scope. Required one of: [sessions:manage]"
}
```
- 원인: Access Token에 `sessions:manage` scope가 없음
- 해결: authorize 시 해당 scope를 포함해 재인증 (클라이언트의 allowedScopes에도 있어야 함)

---

## Security Notes

1. **Access Token**: RS256 JWT, 15분 만료. 일반 API는 서명/만료만 검증 (Resource Server 로컬 공개키 검증)
2. **Refresh Token**: opaque 문자열, 14일 만료, DB에 해시 저장 (user_sessions)
3. **토큰 로테이션 + Reuse detection**: 갱신 시 새 토큰 쌍 발급, 이전 Refresh Token 즉시 무효화.
   무효 토큰 재사용 시 세션 전체 무효화
4. **민감한 API**: Redis JTI 블랙리스트 추가 검증
   - `/api/sessions/**`
   - `/api/auth/change-password`
   - `/api/users/me`
   - `/api/payments/**`
5. **동시 세션 제한**: 최대 5개, 초과 시 가장 오래된 세션 자동 무효화
6. **세션-SAS 브리징**: 세션 무효화 시 SAS authorization(Refresh Token)도 함께 제거됨
