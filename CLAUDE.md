# CLAUDE.md

## Project Overview

**Hyfata REST API** - Spring Boot 3.4.4 기반 OAuth 2.0 + PKCE 인증 API (Java 17, Gradle)

## Quick Commands

```bash
./gradlew build                                      # 빌드
./gradlew build -x test                             # 테스트 제외 빌드
./gradlew bootRun -Dspring.profiles.active=dev      # 실행
./gradlew test                                      # 테스트
./gradlew compileJava                               # 컴파일만
```

## Work Completion Checklist

**모든 작업 완료 후 반드시 다음 순서로 실행:**

1. `./gradlew test` - 모든 테스트 실행
2. `./gradlew build` - 빌드 확인 (테스트 포함)
3. `./gradlew bootRun -Dspring.profiles.active=dev` - 애플리케이션 정상 실행 확인

⚠️ 테스트 또는 빌드 실패 시 수정 후 다시 위 순서대로 진행

## Architecture

```
controller/     → REST 엔드포인트 (AuthController, OAuthController, SessionController, ClientController)
service/        → 비즈니스 로직 (interface + impl/ 구현체)
repository/     → JPA Repository
  └── agora/    → Agora Repository (23개)
entity/         → JPA 엔티티 (User, Client, UserSession, AuthorizationCode)
  └── agora/    → Agora 엔티티 (23개 - 아래 참조)
dto/            → 요청/응답 DTO
util/           → 유틸리티 (JwtUtil, PkceUtil, IpUtil, DeviceDetector, GeoIpService)
config/         → 설정 (SecurityConfig, RedisConfig)
security/       → JwtAuthenticationFilter
scheduler/      → OAuthCleanupScheduler (만료 코드 정리)
```

## Key Features

### OAuth 2.0 + PKCE
- `/oauth/authorize` - Authorization 요청
- `/oauth/login` - 로그인 처리 → Authorization Code 발급
- `/oauth/token` - Token 교환 (`authorization_code`, `refresh_token`)
- `/oauth/logout` - 로그아웃 (세션 무효화, 토큰 블랙리스트)

### Session Management
- `/api/sessions` - 활성 세션 목록
- `/api/sessions/{id}` - 특정 세션 무효화
- `/api/sessions/others` - 현재 세션 제외 모두 무효화
- 최대 5개 동시 세션, Redis 기반 토큰 블랙리스트

### Legacy Auth (Deprecated)
- `/api/auth/register` - 회원가입 (사용 가능)
- `/api/auth/login` - 로그인 (**Deprecated**, OAuth 사용 권장)

## Security Flow

1. 클라이언트가 code_verifier 생성 → SHA256 해싱 → code_challenge
2. `/oauth/authorize?client_id=...&code_challenge=...` 로 요청
3. 사용자 로그인 → Authorization Code 발급
4. `/oauth/token` 에서 code_verifier로 PKCE 검증 → Access/Refresh Token 발급
5. 토큰 갱신 시 기존 세션 무효화 + 새 세션 생성 (Token Rotation)

## Dependencies

| Component | Version |
|-----------|---------|
| Spring Boot | 3.4.4 |
| Java | 17 |
| PostgreSQL | 12+ |
| Redis | 6+ |
| JJWT | 0.12.3 |

## Configuration

환경변수는 `.env` 파일에서 관리 (DB, JWT, Redis, Mail 설정)

## Testing

- `test/OAUTH2_PKCE_TESTING.md` - Postman 테스트 가이드
- `test/OAuth2_PKCE_Complete_Testing.json` - Postman 컬렉션

## Agora Module (Entity/Repository 완료)

실시간 채팅 및 협업 기능 (Service/Controller 미구현)

| 분류 | 엔티티 |
|------|--------|
| 사용자 | AgoraUserProfile, TeamProfile, UserSettings, BlockedUser |
| 친구 | Friend, FriendRequest |
| 채팅 | Chat, ChatParticipant, ChatFolder, ChatFolderItem |
| 메시지 | Message, MessageAttachment, MessageReadStatus |
| 팀 | Team, TeamMember, TeamRole, Notice, Todo, Event |
| 기타 | Notification, FcmToken, AgoraFile, FileMetadata |

## Notes

- `FirstService`, `FirstServiceImpl`은 레거시 테스트 코드 (삭제 가능)
