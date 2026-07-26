# OAuth 2.0 Scopes 설계 문서

> **Status**: Design Draft  
> **Goal**: 타사(Third-Party) 클라이언트와 공식(Official) 클라이언트의 권한을 Scope 단위로 분리하여, 민감한 사용자 정보 변경은 오직 공식 사이트에서만 가능하도록 한다.

---

## 1. 배경 및 문제 정의

현재 시스템은 **모든 OAuth 클라이언트를 동등하게 취급**합니다. 타사 앱이 발급받은 JWT로도 다음 API를 호출할 수 있습니다.

- `POST /api/account/password` — 비밀번호 변경
- `POST /api/auth/enable-2fa` / `disable-2fa` — 2FA 토글
- `POST /api/account/deactivate` — 계정 비활성화
- `DELETE /api/account` — 계정 탈퇴

이는 보안상 큰 문제입니다. 사용자가 타사 앱에 로그인했을 때, 그 앱이 사용자의 비밀번호를 바꾸거나 2FA를 끌 수 있으면 안 됩니다.

### 현재 JWT 구조의 한계

현재 Access Token 클레임:
```json
{
  "sub": "user@example.com",
  "jti": "...",
  "iat": 1234567890,
  "exp": 1234568790
}
```

**토큰이 어떤 클라이언트(client_id)에서 발급되었는지, 어떤 권한(scope)을 가지는지 전혀 알 수 없습니다.**

---

## 2. 설계 목표

1. **OAuth 표준 준수**: RFC 6749의 Scope 개념을 따른다.
2. **역할 기반 접근 제어**: 공식 사이트는 전체 권한, 타사 앱은 제한된 권한만 부여.
3. **하위 호환성 유지**: 기존 클라이언트 앱은 점진적으로 마이그레이션할 수 있도록 단계적 도입.
4. **Flutter 연동 친화적**: 모바일 앱에서도 Scope 요청/처리가 간단해야 함.

---

## 3. Scope 정의

### 3.1 기본 Scope (OpenID Connect 스타일)

| Scope | 설명 | 타사 앱 기본 | 공식 사이트 |
|-------|------|-------------|------------|
| `openid` | 사용자 식별자(sub) 접근 | ✅ | ✅ |
| `profile` | 기본 프로필 조회 (username, displayName) | ✅ | ✅ |
| `email` | 이메일 주소 및 인증 상태 접근 | ✅ | ✅ |

### 3.2 민감 Scope (공식 사이트 전용 권장)

| Scope | 설명 | 타사 앱 | 공식 사이트 |
|-------|------|---------|------------|
| `profile:write` | 프로필 수정 (username, 이름 등) | ❌ | ✅ |
| `account:password` | 비밀번호 변경 | ❌ | ✅ |
| `account:manage` | 계정 비활성화/탈퇴 | ❌ | ✅ |
| `2fa:manage` | 2FA 활성화/비활성화 | ❌ | ✅ |
| `sessions:manage` | 세션 목록 조회 및 원격 로그아웃 | ❌ | ✅ |

### 3.3 Scope 상속 및 포함 관계

```
profile        → profile:read (implicit)
profile:write  → profile:read 포함 (write 권한이 있으면 read도 가능)
account:manage → account:password 포함 (계정 관리자는 비밀번호 변경 가능)
```

---

## 4. 아키텍처 변경 설계

### 4.1 개념도 (변경 후)

```
┌─────────────┐     ┌─────────────────────────────┐     ┌─────────────┐
│   Client    │────▶│  /oauth/authorize           │────▶│  User       │
│ Application │     │  ?scope=profile:read+email  │     │ (로그인)    │
└─────────────┘     └─────────────────────────────┘     └──────┬──────┘
                                                               │
                          ┌────────────────────────────────────┘
                          ▼
              ┌───────────────────────┐
              │  Authorization Code   │  ← scope 저장
              └───────────┬───────────┘
                          │
              ┌───────────▼───────────┐
              │  POST /oauth/token    │
              │  scope + client_id    │
              └───────────┬───────────┘
                          ▼
              ┌───────────────────────┐
              │  JWT Access Token     │
              │  {                    │
              │    "sub": "...",      │
              │    "scope": "profile  │
              │            :read      │
              │            email",    │
              │    "client_id":       │
              │            "..."      │
              │  }                    │
              └───────────┬───────────┘
                          ▼
              ┌───────────────────────┐
              │  API Gateway/Filter   │
              │  scope 검증           │
              └───────────┬───────────┘
                          ▼
              ┌───────────────────────┐
              │  /api/account/password│  ← account:password 필요
              │  403 Forbidden        │     (타사 앱은 거부)
              └───────────────────────┘
```

---

## 5. 데이터 모델 변경

### 5.1 `oauth2_registered_client` + `client_metadata` 테이블

> **SAS 표준 전환 이후**: 커스텀 `clients` 테이블은 제거되었습니다.
> 프로토콜 정보는 SAS 표준 `oauth2_registered_client` 테이블(scopes 컬럼 = allowedScopes)에 저장되고,
> 정보성 메타데이터(owner, frontendUrl, description, clientType)는 `client_metadata` 테이블에 저장됩니다.
> 두 테이블 모두 `db/sas-schema.sql`(sql.init) / JPA `ddl-auto=update`로 자동 생성됩니다.

### 5.2 ~~`authorization_codes` 테이블~~ (삭제됨)

> **SAS 마이그레이션 이후**: 레거시 `authorization_codes` 테이블은 제거되었습니다 (`V8__drop_authorization_codes.sql`).
> Authorization Code와 승인된 scope는 SAS의 `oauth2_authorization` 테이블(`authorized_scopes` 컬럼)에 저장됩니다.

### 5.3 `user_sessions` 테이블

```sql
-- 세션별 발급된 scope 기록 (토큰 갱신 시 동일 scope 유지)
ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS scopes VARCHAR(500);
```

---

## 6. 엔티티 변경

### 6.1 `ClientMetadata.java` (구 `Client.java` 대체)

```java
@Column(length = 100) @Id
private String clientId;         // SAS RegisteredClient.clientId와 동일

@Enumerated(EnumType.STRING)
private ClientType clientType;   // FIRST_PARTY / THIRD_PARTY (메타데이터)

private String frontendUrl;
private String description;
@ManyToOne private User owner;
```

### 6.2 ~~`AuthorizationCode.java`~~ (삭제됨)

> SAS 마이그레이션으로 엔티티/리포지토리가 제거되었습니다. 승인 scope는 SAS `OAuth2Authorization`이 관리합니다.

### 6.3 `UserSession.java`

```java
@Column(length = 500)
private String scopes;
```

### 6.4 `JdbcRegisteredClientRepository` (SAS 표준) + `RegisteredClientFactory`

저장소는 SAS 표준 `JdbcRegisteredClientRepository`를 사용합니다 (커스텀 어댑터 제거).
등록 경로(`ClientServiceImpl`: third-party, `FirstPartyClientInitializer`: first-party)는
`RegisteredClientFactory`로 동일 규칙의 RegisteredClient를 생성합니다:

```java
// auth/service/impl/RegisteredClientFactory.java
.scopes(s -> s.addAll(scopes))  // 등록 요청의 allowedScopes
// consent: third-party=true, first-party=false
// requireProofKey(true), access 15분 / refresh 14일 / 로테이션
```

---

## 7. OAuth 흐름 (SAS 기준)

> **SAS 마이그레이션 이후**: scope 파싱/검증/저장은 Spring Authorization Server가 수행합니다.
> 커스텀 코드는 `RegisteredClientFactory`(등록 시 RegisteredClient 생성)와
> `OAuth2TokenCustomizer`(JWT 클레임 추가)뿐입니다.

### 7.1 Step 1: `/oauth/authorize` (SAS)

**요청 파라미터 `scope`:**

```http
GET /oauth/authorize?client_id=client_001
  &redirect_uri=https://myapp.com/callback
  &response_type=code
  &state=abc123
  &code_challenge=...&code_challenge_method=S256
  &scope=profile+email
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `scope` | X | 요청 scope (공백 또는 `+`로 구분). 미입력 시 클라이언트의 `allowedScopes` **전체**가 부여됨 |

**검증 동작 (SAS 내장):**
1. 요청된 scope가 `RegisteredClient.scopes`(=`allowedScopes`)의 부분 집합인지 검증
2. 초과 시 `invalid_scope` 에러로 응답
3. THIRD_PARTY 클라이언트는 **consent 화면**이 표시되고, 사용자가 동의한 scope가 저장됨 (`oauth2_authorization_consent` 테이블 — 이후 동일 조합은 consent 생략)
4. FIRST_PARTY 클라이언트는 `requireAuthorizationConsent(false)`로 consent 생략

### 7.2 Step 2: `/oauth/token` (SAS)

**Access Token 발급 시:**
1. SAS가 승인된 scope로 JWT(RS256)를 생성
2. `OAuth2TokenCustomizer`가 `email`, `client_id`, 공백 구분 `scope` 클레임을 추가
3. `SessionBridgingAuthorizationService`가 `UserSession` 생성 시 `scopes` 필드 저장
4. 응답의 `scope` 필드에 발급된 scope 반환

**Refresh Token 갱신 시:**
1. authorization에 저장된 scope가 그대로 유지됨 (갱신으로 scope를 늘리거나 줄일 수 없음)
2. 새 Access Token에 동일한 scope 클레임 포함
3. 세션은 새 Refresh Token으로 교체 (로테이션)

---

## 8. JWT 구조 (SAS 기준)

### 8.1 Access Token 클레임 (현재)

```json
{
  "iss": "https://api.hyfata.kr",
  "sub": "user@example.com",
  "email": "user@example.com",
  "aud": "client_001",
  "jti": "a1b2c3d4...",
  "client_id": "client_001",
  "scope": "profile email",
  "iat": 1714819200,
  "exp": 1714820100
}
```

- 서명 알고리즘: **RS256** (RSA 2048, 공개키는 `GET /oauth/jwks`)
- 유효 시간: **15분** (`expires_in` 응답은 초 단위 900)

### 8.2 클레임 생성 지점

`iss`, `sub`, `aud`, `jti`, `iat`, `exp`, `nbf`와 기본 `scope`는 SAS가 생성하고,
`email`, `client_id`, 공백 구분 `scope`는 `OAuth2TokenCustomizer`가 추가합니다
(`common/config/AuthorizationServerConfig.java`의 `jwtTokenCustomizer` 빈).

```java
// AuthorizationServerConfig.java
if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
    context.getClaims().claim("email", context.getPrincipal().getName());
    context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
    context.getClaims().claim("scope", String.join(" ", context.getAuthorizedScopes()));
}
```

---

## 9. API 접근 제어 메커니즘

### 9.1 방법 1: `@RequireScope` 커스텀 어노테이션 (추천)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireScope {
    String[] value();      // 필요한 scope (OR 조건)
    String[] all() default {};  // 필요한 scope (AND 조건, optional)
}
```

**사용 예시:**

```java
@RestController
@RequestMapping("/api/account")
public class AccountController {

    @PutMapping("/password")
    @RequireScope("account:password")
    public ResponseEntity<?> changePassword(...) { ... }

    @PostMapping("/deactivate")
    @RequireScope("account:manage")
    public ResponseEntity<?> deactivateAccount(...) { ... }
}

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/enable-2fa")
    @RequireScope("2fa:manage")
    public ResponseEntity<?> enableTwoFactor(...) { ... }

    @PostMapping("/disable-2fa")
    @RequireScope("2fa:manage")
    public ResponseEntity<?> disableTwoFactor(...) { ... }
}
```

**Aspect 구현 (현재 — SecurityContext의 SCOPE_ authority 기반):**

Resource Server가 JWT의 `scope` 클레임을 `SCOPE_` prefix `GrantedAuthority`로 변환해 두면
(`SecurityConfig`의 `jwtAuthenticationConverter`), Aspect는 이를 읽어 검증합니다.

```java
@Component
@Aspect
public class ScopeAuthorizationAspect {

    private static final String SCOPE_PREFIX = "SCOPE_";

    @Around("@annotation(requireScope)")
    public Object checkScope(ProceedingJoinPoint joinPoint, RequireScope requireScope) throws Throwable {
        Set<String> tokenScopes = extractTokenScopes();  // SCOPE_ prefix 제거한 scope 집합
        if (tokenScopes == null) {
            throw new AccessDeniedException("JWT token is required");
        }
        // value(): OR 조건 / all(): AND 조건 + 암시적 포함 관계 체크
        ...
    }

    private boolean hasImplicitScope(Set<String> tokenScopes, String required) {
        // profile:write → profile 암시적 포함
        if (required.equals("profile") && tokenScopes.contains("profile:write")) return true;
        // account:manage → account:password 암시적 포함
        if (required.equals("account:password") && tokenScopes.contains("account:manage")) return true;
        return false;
    }
}
```

실제 코드: `common/security/scope/ScopeAuthorizationAspect.java`

### 9.2 방법 2: Spring Security `@PreAuthorize` (대안)

```java
@PreAuthorize("hasAuthority('SCOPE_account:password')")
@PutMapping("/password")
public ResponseEntity<?> changePassword(...) { ... }
```

- **장점**: Spring Security 표준, 추가 코드 거의 없음
- **단점**: JWT의 scope를 Spring Security `GrantedAuthority`로 변환해야 함. `JwtAuthenticationFilter`에서 `authentication.getAuthorities()`에 scope를 넣어야 함.

**현재 코드베이스에서는 방법 1(`@RequireScope` AOP)이 덜 침습적이고 명확합니다.**

---

## 10. 공식 클라이언트 vs 타사 클라이언트 구분

### 10.1 `Client` 등록 시 scope 설정

**공식(First-Party) 클라이언트**는 `application.properties` 또는 환경 변수로 관리되며, 애플리케이션 시작 시 `FirstPartyClientInitializer`가 DB에 시드합니다.

```properties
app.first-party.clients[0].client-id=${OFFICIAL_WEB_CLIENT_ID:hyfata-official-web}
app.first-party.clients[0].client-secret=${OFFICIAL_WEB_CLIENT_SECRET}
app.first-party.clients[0].name=${OFFICIAL_WEB_CLIENT_NAME:Hyfata Official Web}
app.first-party.clients[0].frontend-url=${OFFICIAL_WEB_FRONTEND_URL:https://hyfata.kr}
app.first-party.clients[0].redirect-uris=${OFFICIAL_WEB_REDIRECT_URIS:https://hyfata.kr/oauth/callback}
app.first-party.clients[0].allowed-scopes=${OFFICIAL_WEB_ALLOWED_SCOPES:profile email profile:write account:password account:manage 2fa:manage sessions:manage}
```

**타사(Third-Party) 클라이언트**는 `POST /api/clients/register` API를 통해 등록되며, `clientType`은 `THIRD_PARTY`로 강제 설정됩니다.

```json
// 타사 클라이언트 등록 예시 (POST /api/clients/register, 관리자)
{
  "name": "Third Party App",
  "frontendUrl": "https://third.example.com",
  "redirectUris": ["https://third.example.com/callback"],
  "allowedScopes": "profile email"
}
```
등록된 scope는 SAS `oauth2_registered_client.scopes`에 저장되고, `clientType` 메타데이터는
`client_metadata` 테이블에 `THIRD_PARTY`로 기록됩니다.

### 10.2 클라이언트 scope 요청 제한

`/oauth/authorize`에서 타사 클라이언트가 `account:manage`를 요청하면:

```json
{
  "error": "invalid_scope",
  "error_description": "Requested scope 'account:manage' exceeds client's allowed scopes"
}
```

---

## 11. 클라이언트 연동 참고

- Authorization 요청에 `scope` 파라미터를 공백 구분으로 전달 (미입력 시 `allowedScopes` 전체 부여)
- API 호출 시 `403 Forbidden` + `"Insufficient scope"` 응답을 받으면 해당 scope가 없는 것이므로,
  공식 앱 이용 안내 또는 재인증(더 넓은 scope 요청)이 필요
- Flutter 연동 상세 가이드 문서(`FLUTTER_*.md`)는 제거되었습니다. git 이력에서 복구할 수 있습니다.

---

## 12. 구현 단계별 로드맵

### Phase 1: 데이터 모델 ✅
- [x] `AuthorizationCode` 엔티티에 `scopes` 추가
- [x] `UserSession` 엔티티에 `scopes` 추가
- [x] DB 마이그레이션 파일 작성 (`V6__add_client_type.sql` 추가, scope 필드는 JPA `ddl-auto=update`로 적용)
- [x] `JwtUtil`에 `client_id`, `scope` 클레임 생성/추출 메서드 추가

### Phase 2: OAuth 흐름 통합 ✅
- [x] `/oauth/authorize`에 `scope` 파라미터 파싱 및 검증
- [x] 클라이언트의 `allowedScopes` 초과 요청 시 `invalid_scope` 에러 반환
- [x] `/oauth/token`에서 JWT에 `client_id`, `scope` 클레임 포함
- [x] `UserSession` 생성 시 `scopes` 필드 저장
- [x] Refresh Token 갱신 시 기존 `scopes` 유지

### Phase 3: API 접근 제어 ✅
- [x] `@RequireScope` 어노테이션 및 AOP 인터셉터 구현
- [x] 민감 API에 어노테이션 적용:
  - `/api/account/**` → `account:password`, `account:manage`
  - `/api/auth/enable-2fa`, `/disable-2fa` → `2fa:manage`
  - `/api/sessions/**` → `sessions:manage`
- [x] `security.sensitive-endpoints`에 위 경로 추가 (토큰 블랙리스트 연동)

### Phase 4: 클라이언트 관리 및 가이드 업데이트 ✅
- [x] First-Party / Third-Party 클라이언트 구분 (`ClientType` 추가)
- [x] First-Party 클라이언트를 `application.properties` 설정으로 시드 (`FirstPartyClientInitializer`)
- [x] `/api/clients/register` API는 Third-Party 클라이언트만 생성 가능하도록 제한
- [x] Flutter 공식 앱에 `scope` 파라미터 추가

### Phase 5: Consent 화면 ✅ (SAS 기본 consent)
- [x] THIRD_PARTY 클라이언트는 SAS 기본 consent 화면 표시 (`requireAuthorizationConsent(true)`)
- [x] FIRST_PARTY 클라이언트는 consent 생략 (`requireAuthorizationConsent(false)`)
- [x] 동의 내역은 `oauth2_authorization_consent` 테이블에 저장되어 이후 생략

### Phase 6: Spring Authorization Server 마이그레이션 ✅
- [x] OAuth 프로토콜 레이어를 SAS로 교체 (authorize/token/revoke/introspect/jwks)
- [x] JWT HS512(jjwt) → RS256(SAS, RSA 2048), `/oauth/jwks` 공개
- [x] `JwtUtil`/`PkceUtil`/`AuthorizationCode`/수동 OAuth 서비스 제거
- [x] scope 검증은 SAS가 `RegisteredClient.scopes`(=`allowedScopes`) 기준으로 수행
- [x] Refresh Token 로테이션 + reuse detection (SAS 내장)
- [x] 세션/JTI 블랙리스트는 `SessionBridgingAuthorizationService`로 유지

---

## 13. 운영 참고사항

- 이 프로젝트는 운영 전 DB 신규 생성 기준이며, 레거시 스키마 마이그레이션은 없습니다.
- SAS 표준 테이블은 `src/main/resources/db/sas-schema.sql`이 `spring.sql.init`으로 자동 생성하고,
  JPA 엔티티 테이블(`users`, `user_sessions`, `client_metadata`, `login_history`)은 `ddl-auto=update`가 생성합니다.
- 클라이언트의 scope를 변경하려면: third-party는 재등록, first-party는 `application.properties`의
  `app.first-party.clients[n].allowed-scopes` 수정 후 재시작 (시동 시 자동 동기화).

---

## 14. 보안 고려사항

### 13.1 Scope 탈취 방지
- Access Token은 짧게 (15분). 탈취되어도 위험 최소화.
- Refresh Token 갱신 시 `scope` 변경 불가. 탈취된 refresh로 권한 상승 불가.

### 13.2 하위 호환성
- `scope` 파라미터를 본내지 않는 요청은 클라이언트의 `allowedScopes` **전체**가 부여됩니다 (SAS 기본 동작).
- 제한이 필요하면 클라이언트의 `allowedScopes` 자체를 좁게 설정하세요.

### 13.3 Token Blacklist 연동
- 민감 scope를 요구하는 엔드포인트는 반드시 `sensitive-endpoints`에 등록하여, revoked token으로의 접근을 차단.

---

## 15. 관련 문서

- [AUTH_API.md](AUTH_API.md) — API 스펙
- [SCOPE_API_GUIDE.md](SCOPE_API_GUIDE.md) — Scope 적용 가이드
- RFC 6749 Section 3.3 — Access Token Scope
