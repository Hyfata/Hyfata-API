# OAuth Client Configuration Guide

## Overview

Hyfata REST API는 이제 **Google OAuth, Discord OAuth와 유사한 구조**로 멀티테넌시 인증을 지원합니다. 여러 개의 프론트엔드 애플리케이션이 동일한 API 서버를 사용하여 인증을 처리할 수 있습니다.

## Key Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Hyfata REST API (단일 중앙 서버)                         │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────────────┐  ┌──────────────────┐           │
│  │  Client 1        │  │  Client 2        │           │
│  │  (site1.com)     │  │  (site2.com)     │           │
│  │  clientId:       │  │  clientId:       │           │
│  │  "client_001"    │  │  "client_002"    │           │
│  └──────────────────┘  └──────────────────┘           │
│                                                           │
│  ┌──────────────────┐  ┌──────────────────┐           │
│  │  Client 3        │  │  Client 4        │           │
│  │  (site3.com)     │  │  (app.com)       │           │
│  │  clientId:       │  │  clientId:       │           │
│  │  "client_003"    │  │  "client_004"    │           │
│  └──────────────────┘  └──────────────────┘           │
│                                                           │
│  모든 클라이언트가 동일한 사용자 데이터 사용              │
└─────────────────────────────────────────────────────────┘
```

## 1. 클라이언트 등록

### 1.1 기본 클라이언트
애플리케이션 시작 시 다음의 **기본 클라이언트**가 자동으로 생성됩니다:

```
clientId: "default"
clientSecret: "default-secret-key-change-this-in-production"
name: "Default Client"
frontendUrl: "http://localhost:3000"
redirectUris: ["http://localhost:3000", "http://localhost:3001"]
```

**주의**: 프로덕션 환경에서는 반드시 `clientSecret`을 변경하세요!

### 1.2 새로운 클라이언트 등록 API

새로운 사이트/애플리케이션을 추가하려면 다음 API를 호출하세요.

**엔드포인트**: `POST /api/clients/register`

**요청 본문**:
```json
{
  "name": "My Web App",
  "description": "My awesome web application",
  "frontendUrl": "https://myapp.com",
  "redirectUris": [
    "https://myapp.com/callback",
    "https://myapp.com/auth/callback"
  ],
  "maxTokensPerUser": 5
}
```

**응답**:
```json
{
  "message": "Client registered successfully",
  "client": {
    "id": 2,
    "clientId": "client_1697406234567_4829",
    "clientSecret": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6",
    "name": "My Web App",
    "description": "My awesome web application",
    "frontendUrl": "https://myapp.com",
    "redirectUris": [
      "https://myapp.com/callback",
      "https://myapp.com/auth/callback"
    ],
    "enabled": true,
    "maxTokensPerUser": 5,
    "createdAt": "2024-10-18T10:30:00",
    "updatedAt": "2024-10-18T10:30:00"
  }
}
```

**응답에서 중요한 정보**:
- `clientId`: 로그인/회원가입 요청 시 필수
- `clientSecret`: 보안을 위해 안전하게 저장 (공개하지 마세요)
- `frontendUrl`: 이메일 링크가 이 URL을 기반으로 생성됨

## 2. 회원가입 (클라이언트 지정)

이제 회원가입 시 `clientId`를 지정해야 합니다.

**엔드포인트**: `POST /api/auth/register`

**요청 본문**:
```json
{
  "email": "user@example.com",
  "username": "johndoe",
  "password": "securePassword123!",
  "confirmPassword": "securePassword123!",
  "firstName": "John",
  "lastName": "Doe",
  "clientId": "client_1697406234567_4829"
}
```

**처리 흐름**:
1. `clientId` 검증
2. 사용자 생성
3. **클라이언트의 `frontendUrl`을 기반으로 이메일 검증 링크 생성**
   - 예: `https://myapp.com/verify-email?token=...`

## 3. 로그인 (클라이언트 지정)

로그인 시에도 `clientId`를 지정합니다.

**엔드포인트**: `POST /api/auth/login`

**요청 본문**:
```json
{
  "email": "user@example.com",
  "password": "securePassword123!",
  "clientId": "client_1697406234567_4829"
}
```

**응답**:
```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400000,
  "message": "Login successful"
}
```

## 4. 비밀번호 재설정 (클라이언트 지정)

**엔드포인트**: `POST /api/auth/request-password-reset`

**요청 본문**:
```json
{
  "email": "user@example.com",
  "clientId": "client_1697406234567_4829"
}
```

**처리 흐름**:
1. `clientId` 검증
2. 비밀번호 재설정 토큰 생성
3. **클라이언트의 `frontendUrl`을 기반으로 재설정 링크 생성**
   - 예: `https://myapp.com/reset-password?token=...`

## 5. 클라이언트 정보 조회

**엔드포인트**: `GET /api/clients/{clientId}`

**응답**:
```json
{
  "client": {
    "id": 2,
    "clientId": "client_1697406234567_4829",
    "clientSecret": "...",
    "name": "My Web App",
    "frontendUrl": "https://myapp.com",
    ...
  }
}
```

## 6. 주요 기능

### 6.1 동적 이메일 링크
각 클라이언트마다 **고유한 `frontendUrl`을 사용**하여 이메일 링크가 생성됩니다:

| 이벤트 | Client 1 링크 | Client 2 링크 |
|--------|-----------|-----------|
| 이메일 검증 | `https://site1.com/verify-email?token=...` | `https://site2.com/verify-email?token=...` |
| 비밀번호 재설정 | `https://site1.com/reset-password?token=...` | `https://site2.com/reset-password?token=...` |

### 6.2 클라이언트 검증
모든 인증 요청 시 `clientId`가 다음과 같이 검증됩니다:
- ✅ 클라이언트 존재 여부
- ✅ 클라이언트 활성화 상태 (`enabled = true`)
- ❌ 클라이언트가 없거나 비활성화된 경우 → 요청 거부

### 6.3 다중 사용자 계정
**동일한 이메일로 여러 클라이언트에 가입 가능**:
- `user@example.com`이 Client 1에 가입
- `user@example.com`이 Client 2에 가입 (동일 이메일, 동일 비밀번호 사용 가능)
- 두 계정은 **동일한 JWT 토큰**으로 인증됨

## 7. 실제 예시

### 예시: 두 개의 독립적인 사이트

#### Site 1: site1.com
```
clientId: "client_001"
clientSecret: "secret_001"
frontendUrl: "https://site1.com"
```

#### Site 2: site2.com
```
clientId: "client_002"
clientSecret: "secret_002"
frontendUrl: "https://site2.com"
```

#### 사용자 john이 두 사이트 모두에 가입
```
Site 1 가입:
POST /api/auth/register
{
  "email": "john@example.com",
  "username": "john_site1",
  "password": "pass123",
  "clientId": "client_001"
}

Site 2 가입:
POST /api/auth/register
{
  "email": "john@example.com",  ← 동일 이메일 가능!
  "username": "john_site2",
  "password": "pass123",         ← 동일 비밀번호 사용 가능!
  "clientId": "client_002"
}

두 계정 모두 동일한 사용자 엔터티를 사용하지만,
clientId로 어느 사이트에서 왔는지 구분됨
```

## 8. 보안 고려사항

### ✅ 권장사항
- `clientSecret`을 안전하게 저장 (환경 변수, 비밀 관리 시스템)
- 프로덕션 환경에서는 HTTPS 사용
- 정기적으로 `clientSecret` 재발급
- 사용하지 않는 클라이언트는 `enabled = false`로 설정

### ⚠️ 주의사항
- `clientSecret`을 절대 프론트엔드 코드에 노출하지 마세요
- 요청 시 `clientId`는 프론트엔드에서 전송 가능
- `clientSecret`은 백엔드에서만 사용 (향후 구현)

## 9. 데이터베이스 스키마

### clients 테이블
```sql
CREATE TABLE clients (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(100) UNIQUE NOT NULL,
    client_secret VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    frontend_url VARCHAR(255) NOT NULL,
    redirect_uris TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_tokens_per_user INTEGER NOT NULL DEFAULT 5,
    owner_email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## 10. 향후 개선사항

- [ ] OAuth 2.0 Authorization Code Flow 구현
- [ ] PKCE (Proof Key for Code Exchange) 지원
- [ ] 클라이언트별 권한(scope) 관리
- [ ] 클라이언트 수정/삭제 API
- [ ] 클라이언트별 로그 추적
- [ ] 클라이언트별 비율 제한(Rate Limiting)
- [ ] 웹훅(Webhook) 지원
