# OAuth 2.0 Authentication API Reference

Hyfata REST API의 OAuth 2.0 인증 시스템 문서입니다.

> **Spring Authorization Server(SAS) 기반**으로 동작합니다. 토큰은 **RS256**(RSA)으로 서명되며,
> 모든 클라이언트에 **PKCE(S256)가 필수**입니다.

---

## 목차

1. [개요](#개요)
2. [인증 흐름](#인증-흐름)
   - [Authorization Code Flow + PKCE](#authorization-code-flow--pkce)
   - [Token 갱신 Flow (Refresh Token 로테이션)](#token-갱신-flow-refresh-token-로테이션)
3. [OAuth 엔드포인트 (SAS 제공)](#oauth-엔드포인트-sas-제공)
4. [OAuth 페이지/로그아웃 (커스텀)](#oauth-페이지로그아웃-커스텀)
5. [Auth Controller](#auth-controller)
6. [Client Controller](#client-controller)
7. [Session Controller](#session-controller)
8. [토큰 스펙](#토큰-스펙)
9. [DTO Reference](#dto-reference)
10. [에러 응답](#에러-응답)

---

## 개요

Hyfata REST API는 **OAuth 2.0/2.1 Authorization Code Grant + PKCE**를 지원합니다.
프로토콜 처리(authorize, token, revoke, introspect, JWKS)는 Spring Authorization Server가 담당하고,
로그인/회원가입 페이지와 세션 관리는 커스텀 구현입니다.

### 아키텍처 개요

```
┌─────────────┐     ┌──────────────────────────┐     ┌─────────────┐
│   Client    │────▶│  Hyfata API              │────▶│  Database   │
│ Application │◀────│  (Spring Authorization   │◀────│  (PostgreSQL)│
└─────────────┘     │   Server + 커스텀 세션)    │     └─────────────┘
                    └──────────────────────────┘
                            │
                            ▼
                    ┌─────────────┐
                    │    Redis    │
                    │ (JTI 블랙리스트 + 로그인 세션) │
                    └─────────────┘
```

### Base URL

```
https://api.hyfata.kr
```

### 클라이언트 유형

| 유형 | 판별 | 인증 방식 | Consent 화면 |
|------|------|-----------|--------------|
| Confidential | `clientSecret` 존재 | `client_secret_basic` (HTTP Basic 헤더) | FIRST_PARTY: 생략 / THIRD_PARTY: 표시 |
| Public | `clientSecret` 없음 | 없음 (`client_id` 파라미터만) | FIRST_PARTY: 생략 / THIRD_PARTY: 표시 |

모든 클라이언트에 **PKCE(S256)가 강제**됩니다 (`requireProofKey`).

---

## 인증 흐름

### Authorization Code Flow + PKCE

PKCE는 선택이 아니라 **필수**입니다.

#### 클라이언트 호출 요약

| 단계       | 엔드포인트 | 호출 주체 | 설명 |
|----------|-----------|----------|------|
| Step 0   | - | **클라이언트 앱** | code_verifier, code_challenge, state 생성 |
| Step 1   | `GET /oauth/authorize` | **클라이언트 앱** | 직접 호출 (브라우저 리다이렉트) |
| Step 2~4 | `/oauth/login` | **브라우저** | 로그인 페이지 폼 제출, 클라이언트가 직접 호출 X |
| Step 5   | `POST /oauth/token` | **클라이언트 앱** | 직접 호출 (code_verifier 포함) |

```
┌──────────┐                              ┌──────────┐                              ┌──────────┐
│  Client  │                              │   API    │                              │   User   │
└────┬─────┘                              └────┬─────┘                              └────┬─────┘
     │                                         │                                         │
     │  0. code_verifier 생성 (랜덤 문자열)    │                                         │
     │     code_challenge = SHA256(verifier)   │                                         │
     │     state 생성 (CSRF 방지, 클라이언트 생성)│                                         │
     │                                         │                                         │
     │  1. GET /oauth/authorize                │                                         │
     │    ?client_id=xxx                       │                                         │
     │    &redirect_uri=https://app/callback   │                                         │
     │    &response_type=code                  │                                         │
     │    &state=random123                     │                                         │
     │    &code_challenge=E9Mro...            │                                         │
     │    &code_challenge_method=S256          │                                         │
     │────────────────────────────────────────▶│                                         │
     │                                         │                                         │
     │         2. 로그인 페이지로 리다이렉트     │                                         │
     │◀────────────────────────────────────────│                                         │
     │                                         │                                         │
     │                                         │     3. 로그인 정보 입력 (email/password) │
     │                                         │◀────────────────────────────────────────│
     │                                         │                                         │
     │     (third-party 클라이언트인 경우         │                                         │
     │      consent 화면이 추가로 표시됨)         │                                         │
     │                                         │                                         │
     │  4. Redirect to                         │                                         │
     │     https://app/callback                │                                         │
     │       ?code=AUTH_CODE                   │                                         │
     │       &state=random123                  │                                         │
     │◀────────────────────────────────────────│                                         │
     │                                         │                                         │
     │  5. POST /oauth/token                   │                                         │
     │     grant_type=authorization_code       │                                         │
     │     code=AUTH_CODE                      │                                         │
     │     redirect_uri=https://app/callback   │                                         │
     │     code_verifier=original_verifier     │  ◀── PKCE 검증                          │
     │     (+ Confidential: Basic 인증 헤더)     │                                         │
     │────────────────────────────────────────▶│                                         │
     │                                         │                                         │
     │  6. {access_token, refresh_token, ...}  │                                         │
     │◀────────────────────────────────────────│                                         │
     │                                         │                                         │
```

#### Step 0: PKCE/State 값 생성

클라이언트 앱에서 Authorization 요청 전에 생성합니다.

```javascript
// 1. code_verifier 생성 (43-128자의 랜덤 문자열)
const code_verifier = generateRandomString(128);

// 2. code_challenge 생성 (SHA256 해시 후 Base64URL 인코딩)
const code_challenge = base64URLEncode(sha256(code_verifier));

// 3. state 생성 (CSRF 방지 — 서버가 생성하지 않으므로 클라이언트가 반드시 생성/검증)
const state = generateRandomString(32);
```

#### Step 1: Authorization 요청

클라이언트 앱이 브라우저를 이 URL로 리다이렉트합니다.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `client_id` | O | 등록된 OAuth 클라이언트 ID |
| `redirect_uri` | O | 콜백 URL (등록된 URI와 정확히 일치해야 함) |
| `response_type` | O | `code` 고정 |
| `state` | 권장 | CSRF 방지용. **클라이언트가 생성하고 콜백에서 반드시 검증** (서버 자동 생성 없음) |
| `scope` | X | 요청 scope (공백 또는 `+`로 구분). 미입력 시 클라이언트의 `allowedScopes` 전체 부여 |
| `code_challenge` | O | SHA256 해시된 code_verifier (Base64URL) — **필수** |
| `code_challenge_method` | O | `S256` 고정 |

```http
GET /oauth/authorize?client_id=client_001&redirect_uri=https://myapp.com/callback&response_type=code&state=xyz123&scope=profile+email&code_challenge=E9Mrozoa2owUzA7VLHwAIAKllCOvtQyen8P0xWXomaQ&code_challenge_method=S256
```

#### Step 2~4: 사용자 로그인 및 Authorization Code 발급

> ⚠️ **클라이언트가 직접 호출하지 않음** - 이 과정은 브라우저에서 자동으로 처리됩니다.

1. 미인증 사용자는 로그인 페이지(`/oauth/login`)로 리다이렉트됩니다. 원래 authorize 요청은 서버가 세션에 저장해 둡니다.
2. 사용자가 이메일/비밀번호를 입력하고 폼을 제출합니다 (Spring Security formLogin이 처리).
3. 로그인 성공 시 저장해 둔 authorize 요청이 자동으로 재개됩니다.
4. THIRD_PARTY 클라이언트는 scope 동의(consent) 화면이 표시됩니다. FIRST_PARTY는 생략됩니다.
5. 서버가 Authorization Code를 발급하고 `redirect_uri`로 리다이렉트합니다.

이미 로그인 세션(`HYFATA_SESSION` 쿠키)이 있는 사용자는 로그인 없이 바로 4~5단계로 진행됩니다.

**Callback으로 리다이렉트:**

```
https://myapp.com/callback?code=a1b2c3d4e5f6&state=xyz123
```

클라이언트 앱은 `state`가 요청 시 본인이 생성한 값과 일치하는지 검증한 후 `code`를 추출합니다.

#### Step 5~6: Token 교환

클라이언트 앱이 직접 호출합니다.

**Public 클라이언트:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `grant_type` | O | `authorization_code` 고정 |
| `code` | O | Step 4에서 받은 Authorization Code |
| `client_id` | O | 클라이언트 ID |
| `redirect_uri` | O | Step 1에서 사용한 것과 동일해야 함 |
| `code_verifier` | O | Step 0에서 생성한 원본 verifier |

**Confidential 클라이언트:** HTTP Basic 인증 헤더를 사용합니다 (`client_id`/`client_secret` 폼 파라미터가 아님).

```http
POST /oauth/token
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=a1b2c3d4e5f6&redirect_uri=https://myapp.com/callback&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

**성공 응답:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9...",
  "refresh_token": "k8s3hd...opaque...",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "profile email"
}
```

> **변경 사항:** `expires_in`은 **초 단위(900 = 15분)**입니다. refresh token은 JWT가 아닌 opaque 문자열입니다.

---

### Token 갱신 Flow (Refresh Token 로테이션)

Access Token이 만료되면 Refresh Token으로 새 토큰을 발급받습니다.
**갱신할 때마다 새 Refresh Token이 발급되고 기존 Refresh Token은 무효화됩니다(로테이션).**
무효화된 Refresh Token이 재사용되면 서버가 이를 탈지해 해당 세션 전체를 즉시 무효화합니다 (reuse detection).

#### Request Parameters

**Public 클라이언트:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `grant_type` | O | `refresh_token` 고정 |
| `refresh_token` | O | 가장 최근에 발급받은 Refresh Token |
| `client_id` | O | 클라이언트 ID |

**Confidential 클라이언트:** Basic 인증 헤더 + `grant_type`, `refresh_token` 파라미터.

```http
POST /oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token=k8s3hd...&client_id=client_001
```

#### Success Response

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9...(새 토큰)",
  "refresh_token": "m2j9kl...(새 Refresh Token — 이전 것은 폐기)",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "profile email"
}
```

> **Note:** Refresh Token은 14일 유효합니다. 만료되면 다시 로그인(Authorization Code Flow)이 필요합니다.

---

## OAuth 엔드포인트 (SAS 제공)

Spring Authorization Server가 제공하는 표준 엔드포인트입니다 (경로는 기존과 동일하게 커스터마이즈됨).

### GET /oauth/authorize

Authorization 요청. [인증 흐름](#authorization-code-flow--pkce) 참고.

### POST /oauth/token

토큰 교환/갱신. [인증 흐름](#authorization-code-flow--pkce) 참고.

### POST /oauth/revoke

RFC 7009 토큰 취소. Access Token 또는 Refresh Token을 취소합니다.
Refresh Token을 취소하면 연결된 authorization 전체(모든 토큰)가 무효화됩니다.

클라이언트 인증 필요 (Confidential: Basic 헤더 / Public: `client_id` 파라미터).

```http
POST /oauth/revoke
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/x-www-form-urlencoded

token=k8s3hd...&token_type_hint=refresh_token
```

성공 시 `200 OK` (빈 본문).

### POST /oauth/introspect

RFC 7662 토큰 검사. 토큰의 유효성과 메타데이터를 조회합니다. 클라이언트 인증 필요.

```http
POST /oauth/introspect
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/x-www-form-urlencoded

token=eyJhbGciOiJSUzI1NiJ9...
```

```json
{
  "active": true,
  "sub": "user@example.com",
  "client_id": "client_001",
  "scope": "profile email",
  "iss": "https://api.hyfata.kr",
  "exp": 1735689900,
  "iat": 1735689000,
  "jti": "..."
}
```

### GET /oauth/jwks

RSA 공개키 JWK Set을 공개합니다. 클라이언트/리소스 서버는 이 키로 Access Token(RS256) 서명을 검증할 수 있습니다.

```http
GET /oauth/jwks
```

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "...",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

---

## OAuth 페이지/로그아웃 (커스텀)

**Base Path:** `/oauth`

### GET /oauth/login

로그인 페이지(HTML). SAS가 리다이렉트하거나 직접 접근할 수 있습니다.

#### Query Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `error` | X | 로그인 실패 유형: `credentials`(이메일/비밀번호 오류), `disabled`(비활성 계정), `unverified`(이메일 미인증) |

### POST /oauth/login

> ⚠️ **클라이언트가 직접 호출하지 않음** - 로그인 폼이 제출하는 URL입니다 (Spring Security formLogin 처리).

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `email` | string | O | 사용자 이메일 |
| `password` | string | O | 비밀번호 |

성공 시 저장된 authorize 요청으로 리다이렉트(또는 `/`), 실패 시 `/oauth/login?error=...`로 리다이렉트.

### GET /oauth/register, POST /oauth/register

회원가입 페이지 및 처리. 성공 시 이메일 인증 안내 페이지로 이동합니다.

| Parameter (POST) | Type | Required | Description |
|-----------|------|----------|-------------|
| `email` | string | O | 이메일 |
| `username` | string | O | 사용자명 |
| `password` | string | O | 비밀번호 |

### GET /oauth/forgot-password, POST /oauth/forgot-password

비밀번호 찾기 페이지 및 재설정 메일 발송.

### POST /oauth/logout

OAuth 세션을 종료합니다. 세션 무효화 + Access Token JTI 블랙리스트 + SAS authorization 제거(Refresh Token 무효화) + 서버사이드 로그인 세션 무효화를 함께 수행합니다.

**Authentication Required:** Bearer Token

#### Request

**Query Parameter 방식:**
```http
POST /oauth/logout?refresh_token=k8s3hd...
Authorization: Bearer {access_token}
```

**Request Body 방식:**
```http
POST /oauth/logout
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "refresh_token": "k8s3hd..."
}
```

#### Success Response

```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

#### Error Response

```json
{
  "success": false,
  "error": "refresh_token is required"
}
```

---

## Auth Controller

**Base Path:** `/api/auth`

> **제거됨:** `POST /api/auth/login`, `POST /api/auth/refresh` (Deprecated였던 REST 로그인/갱신 엔드포인트는 삭제되었습니다. OAuth 2.0 흐름을 사용하세요.)

### POST /api/auth/register

새 사용자를 등록합니다.

#### Request Body

```json
{
  "email": "user@example.com",
  "username": "johndoe",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "clientId": "client_001"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `email` | string | O | 이메일 주소 |
| `username` | string | O | 사용자명 |
| `password` | string | O | 비밀번호 |
| `firstName` | string | X | 이름 |
| `lastName` | string | X | 성 |
| `clientId` | string | X | 클라이언트 ID (지정 시 유효성 검증) |

#### Success Response (201 Created)

```json
{
  "message": "회원가입이 완료되었습니다. 이메일을 확인하여 계정을 인증해 주세요."
}
```

---

### POST /api/auth/verify-2fa

2단계 인증 코드를 검증합니다 (레거시 REST 로그인 경로 전용).
Access Token은 SAS와 동일한 RS256 JWT로 발급되며, `expiresIn`은 밀리초(900000)입니다.

#### Request Body

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

#### Success Response (200 OK)

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "opaque...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

---

### POST /api/auth/logout

**Authentication Required:** Bearer Token

현재 세션을 로그아웃합니다.

#### Request Body

```json
{
  "refreshToken": "k8s3hd...",
  "logoutAll": false
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `refreshToken` | string | X | - | Refresh Token |
| `logoutAll` | boolean | X | `false` | 모든 세션 로그아웃 여부 |

#### Success Response (200 OK)

```json
{
  "message": "로그아웃되었습니다."
}
```

---

### POST /api/auth/request-password-reset

비밀번호 재설정 이메일을 요청합니다.

#### Request Body

```json
{
  "email": "user@example.com",
  "clientId": "client_001"
}
```

`clientId`는 선택이며, 지정하면 유효한 클라이언트인지 검증합니다.

#### Success Response (200 OK)

```json
{
  "message": "비밀번호 재설정 링크가 이메일로 발송되었습니다."
}
```

---

### POST /api/auth/reset-password

비밀번호를 재설정합니다. 성공 시 모든 세션이 무효화됩니다.

#### Request Body

```json
{
  "email": "user@example.com",
  "token": "reset_token_from_email",
  "newPassword": "NewSecurePass123!",
  "confirmPassword": "NewSecurePass123!"
}
```

#### Success Response (200 OK)

```json
{
  "message": "비밀번호가 성공적으로 변경되었습니다."
}
```

---

### GET /api/auth/verify-email

이메일 주소를 검증합니다.

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `token` | string | O | 이메일 검증 토큰 |

#### Success Response (200 OK)

```json
{
  "message": "이메일 인증이 완료되었습니다."
}
```

---

### POST /api/auth/enable-2fa

**Authentication Required:** Bearer Token + `2fa:manage` scope

2단계 인증을 활성화합니다.

---

### POST /api/auth/disable-2fa

**Authentication Required:** Bearer Token + `2fa:manage` scope

2단계 인증을 비활성화합니다.

---

## Client Controller

**Base Path:** `/api/clients`

OAuth 클라이언트 애플리케이션을 관리합니다.

### POST /api/clients/register

**Authentication Required:** Bearer Token

새 OAuth 클라이언트를 등록합니다. API로 등록되는 클라이언트는 모두 THIRD_PARTY이며 confidential(client_secret 발급)입니다.

> **Scope 제한 정책:**
> - 관리자(`ROLE_ADMIN`): `allowedScopes`를 자유롭게 지정 가능
> - 일반 사용자(`ROLE_USER`) 및 익명: `allowedScopes`가 무조건 `profile email`로 제한됨. 요청에 다른 값을 담아도 무시됩니다.

> 등록된 클라이언트는 SAS 표준 `oauth2_registered_client` 테이블에 저장되며, 정보성 메타데이터는 `client_metadata` 테이블에 저장됩니다.

#### Request Body

```json
{
  "name": "My Application",
  "description": "A sample OAuth client application",
  "frontendUrl": "https://myapp.com",
  "redirectUris": [
    "https://myapp.com/callback",
    "https://myapp.com/auth/callback"
  ],
  "allowedScopes": "profile email profile:write account:password account:manage 2fa:manage sessions:manage",
  "ownerId": 1
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | O | 클라이언트 이름 |
| `description` | string | X | 설명 |
| `frontendUrl` | string | O | 프론트엔드 URL |
| `redirectUris` | string[] | O | 허용된 리다이렉트 URI 목록 (최소 1개) |
| `allowedScopes` | string | X | 클라이언트가 요청할 수 있는 최대 scope (공백 구분). **관리자만 자유 지정 가능** |
| `ownerId` | integer | X | 소유자 사용자 ID |

#### Success Response (201 Created)

```json
{
  "message": "Client registered successfully",
  "client": {
    "clientId": "client_a1b2c3d4e5f6",
    "clientSecret": "secret_x9y8z7w6v5u4",
    "name": "My Application",
    "description": "A sample OAuth client application",
    "frontendUrl": "https://myapp.com",
    "redirectUris": [
      "https://myapp.com/callback",
      "https://myapp.com/auth/callback"
    ],
    "allowedScopes": "profile email profile:write account:password account:manage 2fa:manage sessions:manage",
    "clientType": "THIRD_PARTY",
    "ownerId": 1,
    "createdAt": "2025-12-03T10:00:00",
    "updatedAt": "2025-12-03T10:00:00"
  }
}
```

> ⚠️ **중요**: `clientSecret`은 이 응답에서만 확인할 수 있습니다. 안전하게 저장하세요.

---

### GET /api/clients/{clientId}

클라이언트 정보를 조회합니다. `clientSecret`은 반환되지 않습니다.

#### Success Response (200 OK)

```json
{
  "client": {
    "clientId": "client_a1b2c3d4e5f6",
    "name": "My Application",
    "description": "A sample OAuth client application",
    "frontendUrl": "https://myapp.com",
    "redirectUris": [
      "https://myapp.com/callback"
    ],
    "allowedScopes": "profile email profile:write account:password account:manage 2fa:manage sessions:manage",
    "clientType": "THIRD_PARTY",
    "ownerId": 1,
    "createdAt": "2025-12-03T10:00:00",
    "updatedAt": "2025-12-03T10:00:00"
  }
}
```

---

### GET /api/clients/exists/{clientId}

클라이언트 존재 여부를 확인합니다.

#### Success Response (200 OK)

```json
{
  "exists": true
}
```

---

## Session Controller

**Base Path:** `/api/sessions`

**Authentication Required:** 모든 엔드포인트에 Bearer Token + `sessions:manage` scope 필요

사용자의 활성 세션을 관리합니다. 세션은 Refresh Token 발급 시 자동 생성되며(기기/IP/위치 추적),
사용자당 최대 5개의 동시 세션이 허용됩니다. 세션 무효화 시 Refresh Token(SAS authorization)도 함께 무효화됩니다.

### GET /api/sessions

활성 세션 목록을 조회합니다.

#### Request Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | O | `Bearer {access_token}` |
| `X-Refresh-Token` | X | 현재 세션 식별용 Refresh Token |

#### Success Response (200 OK)

```json
{
  "totalSessions": 2,
  "sessions": [
    {
      "sessionId": "a1b2c3...sha256hash",
      "deviceType": "Desktop",
      "deviceName": "Chrome on Windows",
      "ipAddress": "192.168.1.100",
      "location": "Seoul, South Korea",
      "lastActiveAt": "2025-12-03T10:30:00",
      "createdAt": "2025-12-03T09:00:00",
      "expiresAt": "2025-12-17T09:00:00",
      "isCurrent": true
    }
  ]
}
```

---

### DELETE /api/sessions/{sessionId}

특정 세션을 무효화합니다 (원격 로그아웃). 해당 세션의 Access Token JTI는 블랙리스트에 등록되고,
SAS authorization(Refresh Token)도 함께 제거됩니다.

#### Success Response (200 OK)

```json
{
  "message": "Session revoked successfully"
}
```

---

### POST /api/sessions/revoke-others

현재 세션을 제외한 모든 세션을 무효화합니다.

#### Request Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | O | `Bearer {access_token}` |
| `X-Refresh-Token` | O | 현재 세션의 Refresh Token |

#### Success Response (200 OK)

```json
{
  "message": "Other sessions revoked successfully"
}
```

---

### POST /api/sessions/revoke-all

모든 세션을 무효화합니다 (전체 로그아웃). 서버사이드 로그인 세션(Redis)도 함께 무효화됩니다.

#### Success Response (200 OK)

```json
{
  "message": "All sessions revoked successfully"
}
```

---

## 토큰 스펙

### Access Token (JWT, RS256)

| 항목 | 값 |
|------|-----|
| 서명 알고리즘 | RS256 (RSA 2048) |
| 공개키 | `GET /oauth/jwks` |
| 유효 시간 | 15분 (900초) |

**Claims:**

| Claim | Description |
|-------|-------------|
| `iss` | Issuer (`https://api.hyfata.kr`) |
| `sub` | 사용자 이메일 |
| `email` | 사용자 이메일 (`sub`과 동일) |
| `client_id` | 토큰을 발급받은 클라이언트 ID |
| `scope` | 부여된 scope (공백 구분 문자열) |
| `jti` | 토큰 고유 ID (로그아웃/세션 무효화 시 블랙리스트 등록 대상) |
| `aud` | 클라이언트 ID |
| `iat`, `exp`, `nbf` | 발급/만료/유효 시작 시각 |

### Refresh Token

| 항목 | 값 |
|------|-----|
| 형식 | Opaque 문자열 (JWT 아님) |
| 유효 시간 | 14일 |
| 로테이션 | 갱신마다 새 토큰 발급, 기존 토큰 즉시 무효화 |
| Reuse detection | 무효화된 토큰 재사용 시 세션 전체 무효화 |

### 세션 연동

- 토큰 발급 시 `user_sessions`에 세션이 생성됩니다 (Refresh Token SHA-256 해시 PK, 기기/IP/위치, JTI).
- Refresh 로테이션 시 세션도 새 Refresh Token으로 교첩니다.
- 세션 무효화(로그아웃/원격 무효화/비밀번호 변경) 시 SAS authorization과 JTI 블랙리스트가 함께 처리됩니다.
- 민감 엔드포인트(`security.sensitive-endpoints`)에서는 매 요청 JTI 블랙리스트를 검사합니다.

---

## DTO Reference

### Request DTOs

| DTO | Fields |
|-----|--------|
| **RegisterRequest** | `email`, `username`, `password`, `firstName`, `lastName`, `clientId` |
| **TwoFactorRequest** | `email`, `code` |
| **LogoutRequest** | `refreshToken`, `logoutAll` |
| **PasswordResetRequest** | `email`, `token`, `newPassword`, `confirmPassword` |
| **ClientRegistrationRequest** | `name`, `description`, `frontendUrl`, `redirectUris`, `allowedScopes`, `ownerId` |

### Response DTOs

| DTO | Fields |
|-----|--------|
| **AuthResponse** | `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `twoFactorRequired`, `message` |
| **ClientResponse** | `clientId`, `clientSecret`, `name`, `description`, `frontendUrl`, `redirectUris`, `allowedScopes`, `clientType`, `ownerId`, `createdAt`, `updatedAt` |
| **SessionListResponse** | `totalSessions`, `sessions` |
| **UserSessionDTO** | `sessionId`, `deviceType`, `deviceName`, `ipAddress`, `location`, `lastActiveAt`, `createdAt`, `expiresAt`, `isCurrent` |

> `/oauth/token`의 응답은 DTO가 아닌 SAS 표준 JSON입니다 (`access_token`, `refresh_token`, `token_type`, `expires_in`(초), `scope`).

---

## 에러 응답

### 일반 에러 형식

```json
{
  "error": "error_code",
  "error_description": "Human readable error message"
}
```

### HTTP 상태 코드

| Status Code | Description |
|-------------|-------------|
| 200 | 성공 |
| 201 | 생성 성공 |
| 400 | 잘못된 요청 (파라미터 오류, 검증 실패) |
| 401 | 인증 실패 (토큰 만료/무효, 클라이언트 인증 실패) |
| 403 | 권한 없음 (scope 부족 등) |
| 404 | 리소스를 찾을 수 없음 |
| 500 | 서버 오류 |

### OAuth 에러 코드

| Error Code | Description |
|------------|-------------|
| `invalid_request` | 요청 파라미터 오류 (code_challenge 누락 포함) |
| `invalid_client` | 클라이언트 인증 실패 |
| `invalid_grant` | Authorization Code/Refresh Token 오류, PKCE 검증 실패, 토큰 재사용 |
| `invalid_scope` | 요청 scope가 클라이언트의 `allowedScopes`를 초과 |
| `unauthorized_client` | 클라이언트가 해당 grant type을 사용할 수 없음 |
| `unsupported_grant_type` | 지원하지 않는 grant type |
| `server_error` | 서버 낸부 오류 |

---

## 보안 고려사항

1. **PKCE 필수**: 모든 클라이언트에 PKCE(S256)가 강제됩니다.
2. **state 필수 생성**: 서버가 자동 생성하지 않으므로 클라이언트가 생성하고 콜백에서 검증해야 합니다.
3. **HTTPS 필수**: 모든 API 호출은 HTTPS를 통해 이루어져야 합니다.
4. **토큰 저장**: Access Token은 메모리에, Refresh Token은 Secure Storage에 저장하세요.
5. **세션 제한**: 사용자당 최대 5개의 동시 세션이 허용됩니다.
6. **Token Rotation**: 토큰 갱신 시 기존 Refresh Token은 즉시 무효화되며, 재사용이 감지되면 세션 전체가 무효화됩니다.
7. **토큰 검증**: Access Token 서명 검증이 필요하면 `/oauth/jwks`의 공개키를 사용하세요.
