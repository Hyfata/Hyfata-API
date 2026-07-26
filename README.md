# Hyfata Auth API

**Production-Ready OAuth 2.0 Authorization Server with Scope-based Access Control**

Spring Boot 3.4.4 + Spring Authorization Server 기반의 OAuth 2.0 인증/인가 서버. Google OAuth, Discord OAuth와 동일한 보안 표준을 따를며, **Scope 기반 세분화된 접근 제어**를 지원합니다.

---

## 주요 기능

### OAuth 2.0 Authorization Code Flow + PKCE (Spring Authorization Server)
- RFC 7636 PKCE **필수** (모든 클라이언트, S256)
- RS256 서명 JWT (공개키: `/oauth/jwks`)
- 일회용 Authorization Code (10분 유효)
- Refresh Token 로테이션 + 재사용 감지 (무효 토큰 재사용 시 세션 전체 무효화)
- Third-Party 클라이언트 consent 화면

### 세션 관리
- 다중 기기 로그인 지원 (최대 5개 세션)
- 세션 목록 조회 (기기 정보, IP, 위치)
- 원격 로그아웃 (다른 기기 세션 무효화)
- Redis 기반 토큰 블랙리스트

### Scope 기반 접근 제어
- OAuth 2.0 Scope 표준 준수 (RFC 6749)
- 클라이언트별 `allowedScopes` 설정 (SAS `oauth2_registered_client.scopes`)
- 민감 API(`account:password`, `2fa:manage` 등)에 `@RequireScope` 적용
- First-Party(공식) 클라이언트만 민감 Scope 사용 가능, 설정 파일로 관리
- Third-Party 클라이언트는 API 등록 시 `profile email`로 제한되거나 관리자가 제한된 Scope만 부여 가능

### 완전한 인증 시스템
- JWT 기반 토큰 (RS256, Access: 15분, Refresh: 14일 opaque)
- BCrypt 비밀번호 암호화
- 2FA (이메일 기반)
- 이메일 검증
- 비밀번호 재설정

### 프로덕션 준비
- PostgreSQL 데이터베이스
- Redis (토큰 블랙리스트 + 서버사이드 세션)
- 상세 로깅

---

## 빠른 시작

### 필수 요구사항
- Java 17+
- PostgreSQL 12+
- Redis 6+
- Gradle 7.6+

### 환경 변수 설정 (.env)

```properties
# Database (required)
DB_URL=jdbc:postgresql://localhost:5432/hyfata_db
DB_USER=postgres
DB_PASSWORD=your_password

# R2DBC (optional, 현재 미사용 시 주석 처리 가능)
# R2DBC_URL=r2dbc:postgresql://localhost:5432/hyfata_db

# SAS Issuer (optional, 기본값 https://api.hyfata.kr)
# AUTH_ISSUER=https://api.hyfata.kr

# JWT RSA 키 (optional — 미설정 시 시작 시 임시 키페어 생성, 개발용)
# JWT_PRIVATE_KEY_PATH=file:./keys/private.pem
# JWT_PUBLIC_KEY_PATH=file:./keys/public.pem

# Redis (required)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Mail (required for email verification, 2FA, password reset, account restore)
MAIL_HOST_NAME=mail.hyfata.kr
MAIL_PORT=587
MAIL_USERNAME=noreply@hyfata.kr
MAIL_PASSWORD=your_password
MAIL_FROM=noreply@hyfata.kr

# Public API URL (required for email links)
API_URL=https://api.hyfata.kr

# CORS (required for production)
CORS_URLS=https://hyfata.kr,https://app.hyfata.kr

# First-Party OAuth Client (official website, required)
OFFICIAL_WEB_CLIENT_ID=hyfata-official-web
OFFICIAL_WEB_CLIENT_SECRET=your_official_client_secret
OFFICIAL_WEB_FRONTEND_URL=https://hyfata.kr
OFFICIAL_WEB_REDIRECT_URIS=https://hyfata.kr/oauth/callback

# GeoIP (optional)
# GEOIP_DATABASE_PATH=./GeoLite2-City.mmdb
# GEOIP_ENABLED=false
```

### 빌드 및 실행

```bash
# 빌드
./gradlew build

# 테스트 없이 빌드
./gradlew build -x test

# 애플리케이션 실행
./gradlew bootRun
```

---

## API 엔드포인트

### OAuth 2.0 엔드포인트

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/oauth/authorize` | Authorization 요청 (SAS, PKCE 필수) |
| POST | `/oauth/token` | Token 발급/갱신 (SAS, `authorization_code`, `refresh_token`) |
| POST | `/oauth/revoke` | 토큰 취소 (SAS, RFC 7009) |
| POST | `/oauth/introspect` | 토큰 검사 (SAS, RFC 7662) |
| GET | `/oauth/jwks` | RSA 공개키 JWK Set (SAS) |
| GET/POST | `/oauth/login` | 로그인 페이지/처리 (formLogin) |
| POST | `/oauth/logout` | OAuth 로그아웃 (인증 필요, 커스텀) |

### 계정 관리

| 메서드 | 엔드포인트 | 설명 | 필요 Scope |
|--------|-----------|------|-----------|
| PUT | `/api/account/password` | 비밀번호 변경 | `account:password` |
| POST | `/api/account/deactivate` | 계정 비활성화 | `account:manage` |
| DELETE | `/api/account` | 계정 영구 삭제 | `account:manage` |
| POST | `/api/account/restore/request` | 계정 복구 이메일 요청 | Public |
| GET | `/api/account/restore/confirm` | 계정 복구 확인 (이메일 링크) | Public |

### 세션 관리

| 메서드 | 엔드포인트 | 설명 | 필요 Scope |
|--------|-----------|------|-----------|
| GET | `/api/sessions` | 활성 세션 목록 조회 | `sessions:manage` |
| DELETE | `/api/sessions/{sessionId}` | 특정 세션 무효화 | `sessions:manage` |
| POST | `/api/sessions/revoke-others` | 현재 세션 외 모두 무효화 | `sessions:manage` |

### 2FA 관리

| 메서드 | 엔드포인트 | 설명 | 필요 Scope |
|--------|-----------|------|-----------|
| POST | `/api/auth/enable-2fa` | 2FA 활성화 | `2fa:manage` |
| POST | `/api/auth/disable-2fa` | 2FA 비활성화 | `2fa:manage` |

### 클라이언트 관리

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/api/clients/register` | Third-Party 클라이언트 등록 (인증 필요) |
| GET | `/api/clients/{clientId}` | 클라이언트 정보 조회 |

> **참고**: First-Party(공식) 클라이언트는 `/api/clients/register`로 등록할 수 없습니다. `application.properties` 또는 환경 변수로 정의되며, 애플리케이션 시작 시 자동으로 시드됩니다.

### 기타 인증 API

| 메서드 | 엔드포인트 | 상태 |
|--------|-----------|------|
| POST | `/api/auth/register` | 회원가입 |
| POST | `/api/auth/verify-2fa` | 2FA 코드 검증 (레거시 REST 경로) |
| POST | `/api/auth/logout` | 로그아웃 |

> **제거됨**: `POST /api/auth/login`, `POST /api/auth/refresh` (OAuth 2.0 흐름으로 대체)

---

## OAuth 2.0 + PKCE 플로우

```
클라이언트 앱                              Hyfata API
     │
     ├─ 1. code_verifier 생성 (128자)
     ├─ 2. code_challenge = SHA256(verifier)
     │
     └─ 3. GET /oauth/authorize ──────────────────>
           ?client_id=...
           &redirect_uri=...
           &scope=profile+email
           &code_challenge=...
           &code_challenge_method=S256
                                          └─ 로그인 페이지 반환
     │
     ├─ 4. 사용자 로그인 ─────────────────────────>
                                          └─ Authorization Code 발급
     │
     ├─ 5. POST /oauth/token ─────────────────────>
           grant_type=authorization_code
           &code=...
           &code_verifier=...             └─ PKCE 검증
           &client_id=...                 └─ 세션 생성
           (confidential은 Basic 인증 헤더)
                                          └─ Access + Refresh Token 발급
     │
     └─ 6. 토큰 사용
```

---

## 테스트

### Postman 컬렉션
`test/` 폴더에 완전한 테스트 컬렉션이 포함되어 있습니다:

- `OAuth2_PKCE_Complete_Testing.json` - Postman 컬렉션
- `OAUTH2_PKCE_TESTING.md` - 테스트 가이드

### 테스트 실행

> **주의**: 기본 셸 Java가 25 이상이면 Gradle 8.13이 실패합니다. Java 21로 실행하세요 (`JAVA_HOME=/root/.sdkman/candidates/java/21.0.11-graal`).

```bash
# 모든 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "*ScopeAuthorizationAspectTest*"

# 테스트 결과 보기
# build/reports/tests/test/index.html
```

---

## 보안 기능

| 기능 | 설명 |
|------|------|
| **PKCE 필수** | Authorization Code 탈취 방지 (RFC 7636, 모든 클라이언트 S256) |
| **State** | CSRF 공격 방지 |
| **토큰 로테이션** | Refresh 시 새 토큰 발급, 기존 무효화 + 재사용 감지 |
| **Scope 기반 접근 제어** | 민감 API에 `@RequireScope` 적용 |
| **JTI 블랙리스트** | 로그아웃 시 Access Token 즉시 무효화 |
| **세션 제한** | 사용자당 최대 5개 동시 세션 |
| **BCrypt** | 비밀번호 해싱 (Salt 자동 생성) |

---

## 아키텍처

```
┌─────────────────────────────────────────┐
│  Hyfata REST API                        │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐    │
│  │  OAuth 2.0 Layer (SAS)          │    │
│  │  - Authorization Code Flow      │    │
│  │  - PKCE Verification (필수)      │    │
│  │  - RS256 JWT / JWKS             │    │
│  └─────────────────────────────────┘    │
│                  ↓                      │
│  ┌─────────────────────────────────┐    │
│  │  Session Management Layer       │    │
│  │  - Multi-device Support         │    │
│  │  - Token Rotation               │    │
│  │  - JTI Blacklist (Redis)        │    │
│  └─────────────────────────────────┘    │
│                  ↓                      │
│  ┌─────────────────────────────────┐    │
│  │  Authentication Layer           │    │
│  │  - JWT Token Management         │    │
│  │  - Scope-based Access Control   │    │
│  │  - 2FA/Email Verification       │    │
│  └─────────────────────────────────┘    │
│                  ↓                      │
│  ┌─────────────────────────────────┐    │
│  │  Data Layer                     │    │
│  │  - PostgreSQL (JPA)             │    │
│  │  - Redis (Blacklist + Session)  │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

---

## 데이터베이스 스키마

| 테이블 | 목적 |
|--------|------|
| `users` | 사용자 정보 및 인증 |
| `clients` | OAuth 클라이언트 정보 (scope 포함) |
| `oauth2_authorization` | SAS authorization (code/token/scope) |
| `oauth2_authorization_consent` | SAS consent 동의 내역 |
| `user_sessions` | 사용자 세션 정보 (scope 포함) |
| `login_history` | 로그인 이력 |

---

## 문서

**상세 문서:**

- [`docs/auth/AUTH_API.md`](docs/auth/AUTH_API.md) — 전체 API 레퍼런스
- [`docs/auth/SCOPE_API_GUIDE.md`](docs/auth/SCOPE_API_GUIDE.md) — Scope 목록 및 API 매핑
- [`docs/auth/OAUTH_SCOPES_DESIGN.md`](docs/auth/OAUTH_SCOPES_DESIGN.md) — Scope 설계 문서
- [Wiki](https://github.com/Hyfata/Hyfata-API/wiki)

---

## 의존성

| Component | Version |
|-----------|---------|
| Spring Boot | 3.4.4 |
| Java | 17 |
| PostgreSQL | 12+ |
| Redis | 6+ |
| Spring Authorization Server | 1.4.x |

---

## 향후 계획

- [x] OAuth 2.0 + PKCE 지원
- [x] 세션 관리 (다중 기기)
- [x] 토큰 로테이션
- [x] OAuth 2.0 Scopes 세분화 및 접근 제어
- [x] Spring Authorization Server 마이그레이션 (RS256/JWKS)
- [ ] Rate Limiting
- [ ] WebAuthn 지원

---

## 라이선스

Apache-2.0

---

**Made with care for secure multi-tenant authentication**
