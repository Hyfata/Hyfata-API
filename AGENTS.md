# Hyfata REST API — Agent Guide

> 이 문서는 AI 코딩 에이전트를 위해 작성되었습니다. 프로젝트의 전체 구조, 기술 스택, 빌드 및 테스트 방법, 코드 스타일 규칙 등을 담고 있습니다.

---

## 프로젝트 개요

**Hyfata REST API**는 Spring Boot 3.4.4 기반의 멀티테넌시 인증 및 소셜 플랫폼 API 서버입니다. 주요 특징은 다음과 같습니다:

- **OAuth 2.0 Authorization Code Flow + PKCE** (RFC 7636) 구현
- **JWT 기반 인증** (Access Token 24시간, Refresh Token 7일)
- **세션 관리** (사용자당 최대 5개 동시 세션, Redis 기반 JTI 블랙리스트)
- **Agora 플랫폼** — 1:1/그룹 채팅(WebSocket STOMP), 친구 관리, 팀(그룹) 기능, 알림, 파일 업로드 등의 소셜 기능

### 기술 스택

| 구성 요소 | 버전/설명 |
|-----------|-----------|
| Java | 17 |
| Spring Boot | 3.4.4 |
| Gradle | 7.6+ (Wrapper 포함) |
| 데이터베이스 | PostgreSQL 12+ (운영), H2 (테스트) |
| 캐시/블랙리스트 | Redis 6+ |
| JPA/Hibernate | Spring Data JPA |
| 보안 | Spring Security, JJWT 0.12.3, BCrypt |
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
│   ├── controller/                        # OAuth, 세션, 계정, 클라이언트 컨트롤러
│   ├── dto/                               # 요청/응답 DTO
│   ├── entity/                            # JPA 엔티티 (User, Client, AuthorizationCode, UserSession, LoginHistory)
│   ├── repository/                        # Spring Data JPA 리포지토리
│   ├── scheduler/                         # OAuthCleanupScheduler (만료 코드 자동 정리)
│   └── service/                           # 서비스 인터페이스 및 구현체
│       └── impl/
├── agora/                                 # 소셜 플랫폼 모듈 (서비스/리포지토리 구현됨, 컨트롤러 미구현)
│   ├── chat/                              # 1:1 채팅, 그룹 채팅, 채팅 폴�더
│   ├── file/                              # 파일 업로드/메타데이터
│   ├── friend/                            # 친구 요청, 차단, 관계 관리
│   ├── notification/                      # 알림 및 FCM 토큰
│   ├── profile/                           # 사용자 프로필 및 설정
│   └── team/                              # 팀(그룹) 관리, 공지, 할일, 일정, 초대
└── common/                                # 공통 모듈
    ├── config/                            # Security, Redis, WebSocket, 파일 저장소 등 설정
    ├── exception/                         # GlobalExceptionHandler
    ├── security/                          # JWT 인증 필터, WebSocket 채널 인터셉터
    ├── service/                           # EmailService
    └── util/                              # JwtUtil, PkceUtil, TokenGenerator, DeviceDetector, GeoIpService, IpUtil

src/main/resources/
├── application.properties                 # 애플리케이션 설정 (환경 변수 기반)
├── .env                                   # 로컬 환경 변수 파일 (spring-dotenv 사용)
├── db/migration/                          # Flyway 스타일 SQL 마이그레이션 파일
│   ├── V1__create_users_table.sql
│   ├── V2__create_clients_table.sql
│   ├── V3__create_authorization_codes_table.sql
│   ├── V4__add_pkce_to_authorization_codes.sql
│   └── V5__encrypt_client_secrets.sql
└── templates/oauth/                       # Thymeleaf 템플릿 (login, register, error, verify-email)

src/test/
├── java/kr/hyfata/rest/api/             # 테스트 클래스
│   ├── service/                           # AuthServiceTest, SessionServiceTest, TokenBlacklistServiceTest
│   └── util/                              # JwtUtilTest, DeviceDetectorTest, IpUtilTest
└── resources/application-test.properties  # 테스트 프로필 설정 (H2 사용)

docs/
├── agora/                                 # Agora API 문서 (한국어)
│   ├── api/                               # API 스펙, 플로우 문서, Flutter 연동 가이드
│   └── ERD.md
└── auth/                                  # 인증 관련 Flutter 연동 문서

test/                                      # Postman 컬렉션 및 테스트 가이드
```

### 중요한 구조적 특징

- **Agora 모듈의 컨트롤러 부재**: `agora` 패키지 아래에는 `entity`, `repository`, `service`, `dto`가 존재하지만, **컨트롤러 클래스가 아직 구현되지 않았습니다**. `docs/agora/api/`의 문서들은 구현될 컨트롤러를 가정하고 작성되어 있습니다.
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

# JWT
JWT_SECRET=minimum-32-characters-strong-secret-key

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
./gradlew test --tests "*JwtUtilTest*"

# 테스트 리포트 보기
open build/reports/tests/test/index.html
```

### 테스트 설정

- **테스트 DB**: H2 In-Memory (`jdbc:h2:mem:testdb`)
- **테스트 프로필**: `@ActiveProfiles("test")`
- **Mocking**: `@MockitoBean`을 사용하여 `EmailService` 등 외부 의존성을 모킹합니다.
- **단위 테스트**: `JwtUtilTest`, `DeviceDetectorTest`, `IpUtilTest`처럼 순수 자바 객체는 단위 테스트로 작성됩니다.
- **통합 테스트**: `AuthServiceTest`, `SessionServiceTest`는 `@SpringBootTest`를 사용한 통합 테스트입니다.

### 테스트 커버리지 현황

현재 테스트 파일은 7개로, 주요 유틸리티와 핵심 인증 서비스에 집중되어 있습니다. Agora 모듈의 테스트는 아직 작성되지 않았습니다.

---

## 코드 스타일 규칙

### 언어 및 주석

- **소스 코드 주석**: 한국어와 영어가 혼용되어 사용됩니다. 핵심 보안 로직(OAuth 흐름, JWT 검증 등)에는 한국어 주석이 많습니다.
- **문서**: `docs/`와 `README.md`는 한국어로 작성되어 있습니다.
- **커밋 메시지**: 한국어 사용이 일반적입니다.

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

### JWT 및 토큰 관리

- **Access Token**: 15분(`jwt.expiration=900000`) — `application.properties` 기준. README에는 24시간으로 되어 있으나 실제 설정은 15분입니다.
- **Refresh Token**: 14일(`jwt.refresh-expiration=1209600000`)
- **JTI (JWT ID)**: 각 Access Token에 고유 JTI를 포함하며, 로그아웃 시 Redis 블랙리스트에 등록됩니다.
- **민감 엔드포인트**: `security.sensitive-endpoints`에 설정된 경로는 블랙리스트된 토큰으로 접근 불가.

### OAuth 2.0 + PKCE

- `code_challenge` + `code_verifier` 검증을 통해 Public Client(모바일/Flutter 앱) 보안 확보
- `state` 파라미터로 CSRF 방지
- Authorization Code는 일회용, 10분 유효

### 세션 관리

- 사용자당 최대 5개 세션 (`session.max-per-user=5`)
- 토큰 로테이션: Refresh 시 새 Refresh Token 발급, 기존 세션 무효화
- 비밀번호 변경 시 모든 세션 무효화

### 비밀번호

- **BCrypt** 해싱 (`BCryptPasswordEncoder`)
- `User` 엔티티의 `accountNonLocked`, `credentialsNonExpired`, `accountNonExpired`, `enabled` 플래그로 계정 상태 관리

---

## 데이터베이스

### 운영 환경

- **PostgreSQL** + JPA/Hibernate (`ddl-auto=update`)
- 마이그레이션 파일은 `src/main/resources/db/migration/`에 수동으로 관리됩니다. (Flyway는 설정되어 있지 않으나, 파일 네이밍은 Flyway 스타일을 따릅니다.)

### 주요 테이블

| 테이블 | 목적 |
|--------|------|
| `users` | 사용자 정보, 2FA, 비밀번호 재설정, 이메일 검증 |
| `clients` | OAuth 클라이언트 정보 (client_id, client_secret, redirect_uri) |
| `authorization_codes` | OAuth Authorization Code 저장 (PKCE 포함) |
| `user_sessions` | 사용자 세션 정보 (Refresh Token 해시, 기기 정보, IP, 위치) |
| `login_history` | 로그인 이력 |
| `agora_user_profiles`, `user_settings` | Agora 프로필 및 설정 |
| `friends`, `friend_requests`, `blocked_users` | 친구 관계 |
| `chats`, `chat_participants`, `messages`, `message_read_status`, `chat_folders` | 채팅 |
| `teams`, `team_members`, `team_invitations`, `team_profiles`, `team_roles` | 팀 |
| `events`, `notices`, `todos` | 팀 부가 기능 |
| `notifications` | 알림 |
| `agora_files`, `file_metadata` | 파일 업로드 |

---

## 개발 시 주의사항

### Agora 모듈 개발

- Agora 도메인의 **컨트롤러를 새로 작성**할 때, `docs/agora/api/` 문서를 참고하세요. 문서에는 요청/응답 스펙, URL 매핑, Flutter 연동 가이드가 상세히 기술되어 있습니다.
- WebSocket 관련 기능은 `WebSocketConfig`와 `ChatWebSocketController` 문서(미구현)를 함께 확인해야 합니다.
- 파일 업로드는 `FileStorageConfig`에서 설정한 경로에 저장됩니다. 설정값을 확인하세요.

### 환경 변수

- `src/main/resources/.env` 파일은 **민감 정보를 포함**하고 있으므로, 절대 버전 관리에 포함하지 마세요. (`.gitignore`에 이미 등록되어 있어야 합니다.)
- `start.sh`도 마찬가지로 민감 정보를 포함할 수 있으므로 주의하세요.

### Redis

- Redis는 **토큰 블랙리스트** 용도로만 사용됩니다. 세션 데이터는 PostgreSQL의 `user_sessions` 테이블에 저장됩니다.
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
