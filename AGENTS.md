# Hyfata REST API — Agent Guide

> 이 문서는 AI 코딩 에이전트를 위해 작성되었습니다. 프로젝트의 전체 구조, 기술 스택, 빌드 및 테스트 방법, 코드 스타일 규칙 등을 담고 있습니다.

---

## 프로젝트 개요

**Hyfata REST API**는 Spring Boot 3.4.4 기반의 멀티테넌시 인증 및 소셜 플랫폼 API 서버입니다. 주요 특징은 다음과 같습니다:

- **Spring Authorization Server(SAS) 기반 OAuth 2.0/2.1** — Authorization Code Flow + PKCE(필수), RS256 JWT, JWKS 공개
- **JWT 기반 인증** (RS256, Access Token 15분, Refresh Token 14일 opaque, 로테이션 + reuse detection)
- **세션 관리** (사용자당 최대 5개 동시 세션, Redis 기반 JTI 블랙리스트)


### 기술 스택

| 구성 요소 | 버전/설명 |
|-----------|-----------|
| Java | 17 |
| Spring Boot | 3.4.4 |
| Gradle | 7.6+ (Wrapper 포함) |
| 데이터베이스 | PostgreSQL 12+ (운영), H2 (테스트) |
| 캐시/블랙리스트 | Redis 6+ |
| JPA/Hibernate | Spring Data JPA |
| 보안 | Spring Security, Spring Authorization Server 1.4.x (RS256), BCrypt |
| 템플릿 엔진 | Thymeleaf (OAuth 로그인/회원가입 페이지) |
| 실시간 통신 | Spring WebSocket (STOMP) |
| 메일 | Spring Mail (SMTP) |
| 빌드 도구 | Gradle (Groovy DSL) |

---

## 프로젝트 구조

```
src/main/java/kr/hyfata/rest/api/
├── HyfataRestApiApplication.java          # 메인 애플리케이션 클래스
├── auth/                                  # 인증/인가 모듈
│   ├── controller/                        # OAuth(페이지/로그아웃), 세션, 계정, 클라이언트 컨트롤러
│   ├── dto/                               # 요청/응답 DTO
│   ├── entity/                            # JPA 엔티티 (User, ClientMetadata, UserSession, LoginHistory)
│   ├── repository/                        # Spring Data JPA 리포지토리
│   └── service/                           # 서비스 인터페이스 및 구현체
│       └── impl/                          # SessionBridgingAuthorizationService, RegisteredClientFactory 포함
└── common/                                # 공통 모듈
    ├── config/                            # SecurityConfig, AuthorizationServerConfig(SAS), Redis 등 설정
    ├── exception/                         # GlobalExceptionHandler
    ├── security/                          # JTI 블랙리스트 필터, scope AOP, WebSocket 인터셉터
    ├── service/                           # EmailService
    └── util/                              # TokenGenerator, DeviceDetector, GeoIpService, IpUtil

src/main/resources/
├── application.properties                 # 애플리케이션 설정 (환경 변수 기반)
├── .env                                   # 로컬 환경 변수 파일 (spring-dotenv 사용)
├── db/sas-schema.sql                      # SAS 표준 테이블 DDL (spring.sql.init로 자동 적용)
└── templates/oauth/                       # Thymeleaf 템플릿 (login, register, error, verify-email)

src/test/
├── java/kr/hyfata/rest/api/             # 테스트 클래스
│   ├── service/                           # Auth/Session/TokenBlacklist/Client/SAS 브리징 테스트
│   ├── config/                            # FirstPartyClientInitializerTest, JwtTokenCustomizerTest
│   ├── security/                          # ScopeAuthorizationAspectTest
│   └── util/                              # DeviceDetectorTest, IpUtilTest
└── resources/application-test.properties  # 테스트 프로필 설정 (H2 사용)

docs/
└── auth/                                  # 인증 관련 문서 및 Flutter 연동 가이드

test/                                      # Postman 컬렉션 및 테스트 가이드
```

### 중요한 구조적 특징

- **서비스 인터페이스 패턴**: 모든 서비스는 인터페이스(`*Service`)와 구현체(`*ServiceImpl`)로 분리되어 있습니다.
- **빌더 패턴 사용**: JPA 엔티티는 Lombok `@Builder`를 적극적으로 사용합니다.

---

## 빌드 및 실행

### 사전 요구사항

- Java 17+
- PostgreSQL 12+
- Redis 6+

### 환경 변수

`src/main/resources/.env` 또는 OS 환경 변수로 설정합니다. 주요 변수:

```properties
# Database
DB_URL=jdbc:postgresql://localhost:5432/hyfata_db
DB_USER=postgres
DB_PASSWORD=...

# SAS Issuer (선택, 기본값 https://api.hyfata.kr)
AUTH_ISSUER=https://api.hyfata.kr

# JWT RSA 키 (선택 — 미설정 시 시작 시 임시 RSA 2048 키페어 생성, 개발용)
JWT_PRIVATE_KEY_PATH=file:./keys/private.pem
JWT_PUBLIC_KEY_PATH=file:./keys/public.pem

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Mail
MAIL_HOST_NAME=mail.hyfata.kr
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
MAIL_FROM=noreply@hyfata.kr

# GeoIP (선택)
GEOIP_DATABASE_PATH=./GeoLite2-City.mmdb
GEOIP_ENABLED=false

# Firebase FCM (선택)
FIREBASE_CONFIG_PATH=./firebase.json
```

### 빌드

```bash
# 전체 빌드 (테스트 포함)
./gradlew build

# 테스트 제외 빌드
./gradlew build -x test
```

### 실행

```bash
# 개발 서버 실행
./gradlew bootRun

# 또는 start.sh 사용 (환경 변수 자동 설정)
./start.sh
```

애플리케이션은 기본적으로 **8080 포트**에서 실행됩니다. `PORT` 환경 변수로 변경 가능합니다.

---

## 테스트

### 테스트 실행

```bash
# 모든 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests "*ScopeAuthorizationAspectTest*"

# 테스트 리포트 보기
open build/reports/tests/test/index.html
```

### 테스트 설정

- **JDK 주의**: Gradle 8.13은 Java 25에서 동작하지 않습니다. 기본 셸 Java가 25이면 `JAVA_HOME`을 Java 21로 지정하세요 (예: `JAVA_HOME=/root/.sdkman/candidates/java/21.0.11-graal ./gradlew test`). `.sdkmanrc`에 `java=21.0.11-graal`이 설정되어 있습니다.
- **테스트 DB**: H2 In-Memory (`jdbc:h2:mem:testdb`)
- **테스트 프로필**: `@ActiveProfiles("test")`
- **Mocking**: `@MockitoBean`을 사용하여 `EmailService` 등 외부 의존성을 모킹합니다.
- **단위 테스트**: `DeviceDetectorTest`, `IpUtilTest`, `ScopeAuthorizationAspectTest`, SAS 어댑터/브리징 테스트 등은 Mockito 기반 단위 테스트로 작성됩니다.
- **통합 테스트**: `AuthServiceTest`, `SessionServiceTest`는 `@SpringBootTest`를 사용한 통합 테스트입니다.

### 테스트 커버리지 현황

현재 테스트 파일은 11개(총 85개 케이스)로, 유틸리티·세션·SAS 매핑/브리징·토큰 커스터마이저 등을 커버합니다.

---

## 코드 스타일 규칙

### 언어 및 주석

- **소스 코드 주석**: 한국어와 영어가 혼용되어 사용됩니다. 핵심 보안 로직(OAuth 흐름, JWT 검증 등)에는 한국어 주석이 많습니다.
- **문서**: `docs/`와 `README.md`는 한국어로 작성되어 있습니다.
- **커밋 메시지**: 영어 사용을 일반적으로 해야합니다.

### 네이밍 및 패턴

- **패키지**: `kr.hyfata.rest.api.{모듈}.{하위도메인}`
- **클래스명**:
  - 컨트롤러: `*Controller`
  - 서비스 인터페이스: `*Service`
  - 서비스 구현체: `*ServiceImpl`
  - 리포지토리: `*Repository`
  - DTO: `*Request`, `*Response`, `*Dto`
  - 엔티티: 단수 명사 (예: `User`, `Chat`, `Team`)
- **메서드명**: camelCase, 동사로 시작

### Lombok 사용 규약

```java
@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User { ... }
```

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService { ... }
```

- `@RequiredArgsConstructor`를 사용하여 `final` 필드에 대한 생성자 주입을 합니다.
- `@Slf4j`를 사용하여 로깅합니다.

### 트랜잭션

- 서비스 계층의 **쓰기 작업**에는 `@Transactional`을 명시합니다.
- 읽기 전용 작업에는 `@Transactional(readOnly = true)`를 권장합니다.

### 예외 처리

- 인증/인가 실패: `BadCredentialsException` (Spring Security)
- 글로벌 예외 처리: `GlobalExceptionHandler` (`@ControllerAdvice`)
- 응답 형식: `Map<String, String>` 또는 `Map<String, Object>`로 JSON 에러 응답 반환

---

## 보안 고려사항

### JWT 및 토큰 관리 (SAS)

- **Access Token**: RS256 JWT, 15분 (SAS `TokenSettings`, 등록 시 `RegisteredClientFactory`로 클라이언트별 설정). 공개키는 `GET /oauth/jwks`.
- **Refresh Token**: opaque 문자열, 14일. 갱신마다 로테이션되며, 무효 토큰 재사용 시 세션 전체 무효화 (SAS reuse detection).
- **토큰 클레임**: `iss`(`auth.issuer`), `sub`/`email`(사용자 이메일), `client_id`, 공백 구분 `scope`, `jti`. 커스텀 클레임은 `AuthorizationServerConfig`의 `jwtTokenCustomizer`가 추가.
- **JTI (JWT ID)**: 각 Access Token에 고유 JTI를 포함하며, 로그아웃/세션 무효화 시 Redis 블랙리스트에 등록됩니다.
- **민감 엔드포인트**: `security.sensitive-endpoints`에 설정된 경로는 Resource Server 인증 후 JTI 블랙리스트를 추가 검사합니다 (`JwtAuthenticationFilter` — 블랙리스트 전용).
- **Resource Server**: API 요청의 JWT 검증은 `oauth2ResourceServer().jwt()` (로컬 RSA 공개키)가 담당하고, scope는 `SCOPE_` prefix authority로 매핑됩니다.
- **RSA 키**: `auth.jwt.private-key`/`auth.jwt.public-key`(PEM 경로)에서 로드. 미설정 시 개발용 임시 키페어를 시작 시 생성 (재시작 시 기존 토큰 무효화).
- **OAuth 로그인 세션**: `/oauth/login`, `/oauth/authorize`는 서버사이드 세션(`Spring Session + Redis`) 기반. 브라우저는 `HYFATA_SESSION` 쿠키로 세션을 유지하며, 로그인은 Spring Security formLogin이 처리합니다.

### OAuth 2.0 + PKCE (SAS)

- 프로토콜 엔드포인트(`/oauth/authorize`, `/oauth/token`, `/oauth/revoke`, `/oauth/introspect`, `/oauth/jwks`)는 SAS가 처리 (`AuthorizationServerConfig`, `@Order(1)` 필터 체인).
- **PKCE(S256)는 모든 클라이언트에 필수** (`requireProofKey`).
- confidential(클라이언트 시크릿 존재)은 `client_secret_basic`(Basic 헤더), public(시크릿 없음)은 `NONE`으로 인증 (`RegisteredClient.scopes`는 등록 시 `allowedScopes`에서 매핑 (저장소는 SAS 표준 `JdbcRegisteredClientRepository`)).
- `state`는 클라이언트가 생성/검증 (서버 자동 생성 없음).
- Authorization Code는 일회용, 10분 유효 (SAS 기본값).
- THIRD_PARTY 클라이언트는 SAS 기본 consent 화면 표시, FIRST_PARTY는 `requireAuthorizationConsent(false)`로 생략.

### 세션 관리

- **API 세션 (SAS 토큰 기반)**: 사용자당 최대 5개 동시 세션 (`session.max-per-user=5`). `user_sessions` 테이블에 Refresh Token 해시(PK), JTI, client_id, SAS authorization_id, 기기/IP/위치가 저장됩니다.
- **세션 브리징**: `SessionBridgingAuthorizationService`(OAuth2AuthorizationService 데코레이터)가 SAS 토큰 발급/로테이션/삭제 시점에 세션 미러링·LoginHistory 기록·JTI 블랙리스트를 수행합니다.
- 세션 무효화(`SessionService`) 시 SAS authorization도 함께 제거되어 Refresh Token까지 무효화됩니다.
- **OAuth 브라우저 세션 (서버사이드 세션)**: `/oauth/login`, `/oauth/authorize`는 `Spring Session + Redis` 기반. `HYFATA_SESSION` 쿠키로 유지됩니다.
- 비밀번호 변경 시 모든 세션 무효화 (API 세션 + OAuth 브라우저 세션 모두 무효화)

### 비밀번호

- **BCrypt** 해싱 (`BCryptPasswordEncoder`)
- `User` 엔티티의 `accountNonLocked`, `credentialsNonExpired`, `accountNonExpired`, `enabled` 플래그로 계정 상태 관리

---

## 데이터베이스

### 운영 환경

- **PostgreSQL** + JPA/Hibernate (`ddl-auto=update`)
- SAS 표준 테이블(`oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`)은 `src/main/resources/db/sas-schema.sql`이 `spring.sql.init`으로 자동 생성합니다. JPA 엔티티 테이블은 `ddl-auto=update`가 생성합니다. (운영 전 DB 신규 생성 기준, 별도 마이그레이션 파일 없음)

### 주요 테이블

| 테이블 | 목적 |
|--------|------|
| `users` | 사용자 정보, 2FA, 비밀번호 재설정, 이메일 검증 |
| `oauth2_registered_client` | SAS RegisteredClient (시크릿, redirect_uri, scopes, client/token settings) |
| `client_metadata` | 클라이언트 정보성 메타데이터 (owner FK → users, frontend_url, description, client_type) |
| `oauth2_authorization` | SAS authorization (code/token/scope, V7) |
| `oauth2_authorization_consent` | SAS consent 동의 내역 (V7) |
| `user_sessions` | 사용자 세션 정보 (Refresh Token 해시, 기기 정보, IP, 위치) |
| `login_history` | 로그인 이력 |


---

## 개발 시 주의사항

### API 문서 동기화

- **인증/인가 관련 API**(`auth` 모듈)의 요청/응답 스펙, 엔드포인트, DTO 등을 변경한 경우, 반드시 **`docs/auth/`** 경로에 있는 md 파일들을 함께 수정하세요.
- 
### 환경 변수

- `src/main/resources/.env` 파일은 **민감 정보를 포함**하고 있으므로, 절대 버전 관리에 포함하지 마세요. (`.gitignore`에 이미 등록되어 있어야 합니다.)
- `start.sh`도 마찬가지로 민감 정보를 포함할 수 있으므로 주의하세요.

### Redis

- Redis는 **토큰 블랙리스트** 및 **OAuth 서버사이드 세션 저장** 용도로 사용됩니다.
- API 세션 데이터(Refresh Token 기반)는 PostgreSQL의 `user_sessions` 테이블에 저장됩니다.
- OAuth 브라우저 세션은 `Spring Session Data Redis`를 통해 Redis에 저장됩니다.
- `TokenBlacklistService`를 통해 JTI의 블랙리스트 등록/조회를 수행합니다.

### GeoIP

- `GeoIpService`는 MaxMind GeoLite2 데이터베이스를 사용합니다. `GEOIP_ENABLED=false`이면 기능이 비활성화됩니다.
- 데이터베이스 파일(`GeoLite2-City.mmdb`)이 없으면 예외가 발생할 수 있으므로, 비활성화 상태에서 개발하세요.

---

## 추가 리소스

- **Postman 컬렉션**: `test/OAuth2_PKCE_Complete_Testing.json`
- **OAuth 테스트 가이드**: `test/OAUTH2_PKCE_TESTING.md`
- **세션 관리 테스트 가이드**: `test/SESSION_MANAGEMENT_TESTING.md`
- **Wiki**: https://github.com/Hyfata/Hyfata-API/wiki
