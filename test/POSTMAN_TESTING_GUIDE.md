# Postman을 이용한 OAuth 2.0 테스트 가이드

이 문서는 Hyfata REST API의 OAuth 2.0 Authorization Code Flow를 Postman에서 테스트하는 방법을 설명합니다.

---

## 📋 목차

1. [사전 준비](#사전-준비)
2. [전체 플로우](#전체-플로우)
3. [단계별 테스트](#단계별-테스트)
4. [Postman 환경 변수 설정](#postman-환경-변수-설정)
5. [트러블슈팅](#트러블슈팅)

---

## 🔧 사전 준비

### 필수 조건
- ✅ Spring Boot 애플리케이션 실행 중: `http://localhost:8080`
- ✅ PostgreSQL 데이터베이스 연결됨
- ✅ Postman 설치됨 (또는 웹 버전 사용)

### 테스트 데이터 준비

테스트를 위해 먼저 다음을 준비해야 합니다:
1. **테스트 사용자** - 이메일 인증된 사용자
2. **테스트 클라이언트** - OAuth 클라이언트 (앱)

---

## 🔄 전체 플로우

OAuth 2.0 Authorization Code Flow는 다음 3단계로 진행됩니다:

```
┌─────────────────────────────────────────────────────────┐
│                      OAuth 2.0 Flow                     │
└─────────────────────────────────────────────────────────┘

1️⃣  클라이언트 등록
    POST /api/clients/register
    ↓
    → clientId, clientSecret 획득

2️⃣  Authorization 요청
    GET /oauth/authorize?client_id=xxx&redirect_uri=xxx
    ↓
    → 로그인 페이지 표시

3️⃣  로그인 및 Authorization Code 생성
    POST /oauth/login
    ↓
    → Authorization Code 발급

4️⃣  Token 교환
    POST /oauth/token
    ↓
    → Access Token, Refresh Token 발급
```

---

## 🎯 단계별 테스트

### 📌 준비 단계 1: 테스트 사용자 생성

먼저 인증된 사용자를 생성해야 합니다.

**요청:**
```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "email": "testuser@example.com",
  "password": "TestPassword123!",
  "name": "Test User"
}
```

**응답:**
```json
{
  "message": "Registration successful. Please verify your email.",
  "user": {
    "id": 1,
    "email": "testuser@example.com",
    "name": "Test User"
  }
}
```

**🔧 이메일 검증 우회 (개발 환경에서):**

데이터베이스에서 직접 이메일 검증 상태를 업데이트합니다:

```sql
UPDATE users SET email_verified = true WHERE email = 'testuser@example.com';
```

---

### 📌 단계 1: 클라이언트 등록

OAuth 클라이언트(앱)를 등록합니다. Google OAuth나 Discord OAuth에서 앱을 등록하는 것과 동일합니다.

**Postman 요청 생성:**

| 항목 | 값 |
|------|-----|
| **메서드** | POST |
| **URL** | `http://localhost:8080/api/clients/register` |
| **Headers** | `Content-Type: application/json` |

**Request Body:**
```json
{
  "name": "My Postman Test App",
  "description": "Test application for OAuth testing",
  "frontendUrl": "http://localhost:3000",
  "redirectUris": [
    "http://localhost:3000/callback",
    "http://localhost:3001/callback"
  ],
  "maxTokensPerUser": 5
}
```

**Response 예시:**
```json
{
  "message": "Client registered successfully",
  "client": {
    "id": 1,
    "clientId": "client_1730784523456_7823",
    "clientSecret": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
    "name": "My Postman Test App",
    "description": "Test application for OAuth testing",
    "frontendUrl": "http://localhost:3000",
    "redirectUris": ["http://localhost:3000/callback", "http://localhost:3001/callback"],
    "maxTokensPerUser": 5,
    "createdAt": "2024-11-05T10:15:23.456Z"
  }
}
```

**💾 응답값 저장:**

응답에서 다음 값을 저장합니다:
- `clientId`: `client_1730784523456_7823`
- `clientSecret`: `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6`

이 값들은 이후 단계에서 필요합니다.

---

### 📌 단계 2: Authorization 요청

사용자를 로그인 페이지로 리다이렉트합니다. 이 단계는 **브라우저에서 직접 접속**해야 합니다.

**URL:**
```
http://localhost:8080/oauth/authorize?client_id=CLIENT_ID&redirect_uri=http://localhost:3000/callback&state=random_state_123&response_type=code
```

**파라미터 설명:**

| 파라미터 | 값 | 설명 |
|---------|-----|------|
| `client_id` | `client_1730784523456_7823` | 클라이언트 ID |
| `redirect_uri` | `http://localhost:3000/callback` | 콜백 URL (반드시 등록된 URI여야 함) |
| `state` | `random_state_123` | CSRF 방지 (임의의 값) |
| `response_type` | `code` | 고정값 |

**🌐 브라우저에서 테스트:**

위 URL을 브라우저 주소창에 복사 후 엔터:

```
http://localhost:8080/oauth/authorize?client_id=client_1730784523456_7823&redirect_uri=http://localhost:3000/callback&state=random_state_123&response_type=code
```

**결과:**
- 로그인 페이지가 표시됩니다.
- `client_id`, `redirect_uri`, `state` 값이 폼에 미리 입력되어 있습니다.

---

### 📌 단계 3: 로그인 처리 및 Authorization Code 생성

Postman에서 로그인 요청을 보냅니다.

**Postman 요청 생성:**

| 항목 | 값 |
|------|-----|
| **메서드** | POST |
| **URL** | `http://localhost:8080/oauth/login` |
| **Content-Type** | `application/x-www-form-urlencoded` |

**Request Body (Form Data):**

```
email=testuser@example.com
password=TestPassword123!
client_id=client_1730784523456_7823
redirect_uri=http://localhost:3000/callback
state=random_state_123
```

**Postman 설정 방법:**
1. **Body** 탭 클릭
2. **x-www-form-urlencoded** 라디오 버튼 선택
3. 다음 Key-Value 쌍 입력:
   - Key: `email` / Value: `testuser@example.com`
   - Key: `password` / Value: `TestPassword123!`
   - Key: `client_id` / Value: `client_1730784523456_7823`
   - Key: `redirect_uri` / Value: `http://localhost:3000/callback`
   - Key: `state` / Value: `random_state_123`

**Response:**
```
HTTP/1.1 302 Found
Location: http://localhost:3000/callback?code=auth_code_1234567890&state=random_state_123
```

**⚠️ 주의사항:**
- Postman에서는 리다이렉트를 자동으로 따라가지 않을 수 있습니다.
- **Follow redirects** 옵션을 비활성화합니다.
- 응답 헤더에서 `Location` 헤더를 확인합니다.

**💾 Authorization Code 추출:**

응답 URL에서 Authorization Code를 추출합니다:

```
http://localhost:3000/callback?code=auth_code_1234567890&state=random_state_123
                                    ↑ 이 값
```

- `code`: `auth_code_1234567890`
- `state`: `random_state_123` (원본과 일치해야 함)

---

### 📌 단계 4: Authorization Code를 Token으로 교환

Authorization Code를 사용하여 Access Token과 Refresh Token을 얻습니다.

**Postman 요청 생성:**

| 항목 | 값 |
|------|-----|
| **메서드** | POST |
| **URL** | `http://localhost:8080/oauth/token` |
| **Content-Type** | `application/x-www-form-urlencoded` |

**Request Body (Form Data):**

```
grant_type=authorization_code
code=auth_code_1234567890
client_id=client_1730784523456_7823
client_secret=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
redirect_uri=http://localhost:3000/callback
```

**Postman 설정:**
1. **Body** 탭 클릭
2. **x-www-form-urlencoded** 선택
3. Key-Value 쌍 입력:
   - Key: `grant_type` / Value: `authorization_code`
   - Key: `code` / Value: `auth_code_1234567890`
   - Key: `client_id` / Value: `client_1730784523456_7823`
   - Key: `client_secret` / Value: `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6`
   - Key: `redirect_uri` / Value: `http://localhost:3000/callback`

**Response:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlckBleGFtcGxlLmNvbSIsImlhdCI6MTczMDc4NDcyMywiZXhwIjoxNzMwODcxMTIzfQ.aBcDeFgHiJkLmNoPqRsTuVwXyZ",
  "refresh_token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlckBleGFtcGxlLmNvbSIsImlhdCI6MTczMDc4NDcyMywiZXhwIjoxNzMxMzg5MTIzfQ.aBcDeFgHiJkLmNoPqRsTuVwXyZ",
  "token_type": "Bearer",
  "expires_in": 86400000,
  "scope": "user:email user:profile"
}
```

**💾 토큰 저장:**

응답에서 다음 값을 저장합니다:
- `access_token`: JWT 토큰 (24시간 유효)
- `refresh_token`: 새로운 토큰 발급용 (7일 유효)

---

## 📊 Postman 환경 변수 설정

여러 번의 테스트를 쉽게 하기 위해 환경 변수를 설정합니다.

### 환경 변수 생성

**Postman에서:**

1. **Environment** 아이콘 클릭 (왼쪽 사이드바)
2. **Create new environment** 클릭
3. Environment 이름: `OAuth Local Testing`
4. 다음 변수 추가:

| 변수명 | 초기값 | 설명 |
|-------|-------|------|
| `base_url` | `http://localhost:8080` | API 기본 URL |
| `client_id` | `` | 클라이언트 ID |
| `client_secret` | `` | 클라이언트 Secret |
| `email` | `testuser@example.com` | 테스트 사용자 이메일 |
| `password` | `TestPassword123!` | 테스트 사용자 비밀번호 |
| `redirect_uri` | `http://localhost:3000/callback` | 리다이렉트 URI |
| `state` | `` | State 파라미터 |
| `auth_code` | `` | Authorization Code |
| `access_token` | `` | Access Token |
| `refresh_token` | `` | Refresh Token |

### 환경 변수 사용

요청에서 변수를 사용하려면 `{{변수명}}` 형식으로 입력합니다:

**예시:**
```
POST {{base_url}}/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
code={{auth_code}}
client_id={{client_id}}
client_secret={{client_secret}}
redirect_uri={{redirect_uri}}
```

---

## 🎬 완전한 테스트 시나리오

### 시나리오 1: 전체 OAuth 플로우

**요청 1: 클라이언트 등록**
```
POST {{base_url}}/api/clients/register
Content-Type: application/json

{
  "name": "Postman Test App",
  "description": "Test",
  "frontendUrl": "http://localhost:3000",
  "redirectUris": ["http://localhost:3000/callback"],
  "maxTokensPerUser": 5
}
```

응답에서 `client_id`, `client_secret` 저장 → 환경 변수에 입력

**요청 2: Authorization 요청 (브라우저에서)**
```
http://localhost:8080/oauth/authorize?client_id={{client_id}}&redirect_uri={{redirect_uri}}&state=test_state_123&response_type=code
```

로그인 폼 표시 → 사용자명/비밀번호 입력

**요청 3: 로그인 처리**
```
POST {{base_url}}/oauth/login
Content-Type: application/x-www-form-urlencoded

email={{email}}
password={{password}}
client_id={{client_id}}
redirect_uri={{redirect_uri}}
state=test_state_123
```

응답 URL에서 `code` 추출 → 환경 변수 `auth_code`에 입력

**요청 4: Token 교환**
```
POST {{base_url}}/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
code={{auth_code}}
client_id={{client_id}}
client_secret={{client_secret}}
redirect_uri={{redirect_uri}}
```

응답에서 `access_token`, `refresh_token` 저장

---

## 🧪 추가 테스트

### 1️⃣ Protected 엔드포인트 테스트

Access Token을 사용하여 보호된 엔드포인트 접근:

```
GET {{base_url}}/api/protected/user
Authorization: Bearer {{access_token}}
```

### 2️⃣ Refresh Token 테스트

Access Token이 만료되었을 때 새 토큰 발급:

```
POST {{base_url}}/api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "{{refresh_token}}"
}
```

### 3️⃣ 클라이언트 조회

등록된 클라이언트 정보 조회:

```
GET {{base_url}}/api/clients/{{client_id}}
```

---

## ⚠️ 트러블슈팅

### 문제 1: "Invalid client" 에러

**원인:**
- 클라이언트 ID가 잘못됨
- 클라이언트가 등록되지 않음

**해결:**
1. 클라이언트 등록 요청 다시 실행
2. 응답의 `clientId` 값 확인
3. 정확한 `clientId` 사용

---

### 문제 2: "Invalid redirect URI" 에러

**원인:**
- 요청한 redirect_uri가 클라이언트 등록 시 지정한 URI와 다름

**해결:**
1. 클라이언트 등록 시 지정한 redirect_uri 확인
2. 모든 요청에서 동일한 redirect_uri 사용
3. 필요하면 새 클라이언트 등록 후 다시 시작

---

### 문제 3: "Invalid email or password" 에러

**원인:**
- 이메일이 존재하지 않음
- 비밀번호가 틀림
- 사용자가 비활성화됨

**해결:**
1. 사용자 등록 요청 다시 실행
2. 이메일 검증 여부 확인 (DB에서 `email_verified = true` 확인)
3. 비밀번호 정확성 확인

---

### 문제 4: "Authorization code is required" 또는 "Invalid authorization code" 에러

**원인:**
- Authorization code가 없음
- 코드가 만료됨 (10분)
- 코드가 이미 사용됨

**해결:**
1. 로그인 요청 다시 실행하여 새 코드 획득
2. 코드가 10분 이내에 사용되는지 확인
3. 코드는 한 번만 사용 가능

---

### 문제 5: Postman에서 리다이렉트가 자동 따라가지 않음

**해결:**
1. **Settings** → **General** → **Automatically follow redirects** 체크 해제
2. 응답의 **Headers** 탭에서 `Location` 헤더 확인
3. 리다이렉트 URL에서 파라미터 추출

---

## 📝 참고 사항

### Security Best Practices

1. **클라이언트 Secret 보호**: 프로덕션 환경에서 절대 노출 금지
2. **HTTPS 사용**: 프로덕션에서는 HTTPS 필수
3. **State 파라미터**: CSRF 공격 방지를 위해 항상 사용
4. **Redirect URI 검증**: 화이트리스트 기반 검증 필수

### 토큰 유효 기간

- **Access Token**: 24시간
- **Refresh Token**: 7일
- **Authorization Code**: 10분

---

## 🚀 다음 단계

1. 여러 클라이언트로 테스트
2. 여러 사용자로 테스트
3. Token Refresh 플로우 테스트
4. Error 시나리오 테스트
5. 프론트엔드와 통합 테스트

---

## 📚 추가 참고 자료

- [OAuth 2.0 공식 스펙](https://tools.ietf.org/html/rfc6749)
- [Authorization Code Flow 상세 가이드](./OAUTH_2_AUTHORIZATION_CODE_FLOW.md)
- [API 인증 문서](./API_AUTHENTICATION.md)
- [클라이언트 가이드](./OAUTH_CLIENT_GUIDE.md)

---

**마지막 업데이트:** 2024-11-05