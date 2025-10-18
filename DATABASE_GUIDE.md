# 데이터베이스 가이드

## 📋 개요

Hyfata REST API는 **PostgreSQL** 기반의 JPA 데이터베이스를 사용합니다.

**설정:**
- **데이터베이스**: PostgreSQL
- **Host**: 192.168.1.4
- **Port**: 5432
- **Database**: rest_api
- **ORM**: Hibernate (Spring Data JPA)
- **마이그레이션**: Hibernate auto-update (개발), SQL 스크립트 (프로덕션)

---

## 🏗️ 데이터베이스 아키텍처

### 계층 구조

```
┌─────────────────────────────────────┐
│         Controller Layer             │  (API 엔드포인트)
├─────────────────────────────────────┤
│         Service Layer               │  (비즈니스 로직)
├─────────────────────────────────────┤
│      Repository Layer (DAO)         │  (데이터 접근)
│                                     │
│  UserRepository (JPA Repository)    │
├─────────────────────────────────────┤
│      Entity Layer (Domain Model)    │  (데이터 모델)
│                                     │
│  User (JPA Entity)                  │
├─────────────────────────────────────┤
│      Hibernate ORM                  │  (SQL 생성)
├─────────────────────────────────────┤
│    PostgreSQL Driver (JDBC)         │  (연결)
├─────────────────────────────────────┤
│       PostgreSQL Database           │  (물리적 저장)
└─────────────────────────────────────┘
```

---

## 📦 DB 관련 클래스

### 1. User 엔티티 (`src/main/java/kr/hyfata/rest/api/entity/User.java`)

**역할**: PostgreSQL의 `users` 테이블을 매핑하는 JPA 엔티티

**주요 특징:**
- JPA `@Entity` 애노테이션
- Spring Security `UserDetails` 구현
- Builder 패턴 지원

**필드 구조:**

| 필드명 | 타입 | 설명 | 제약 |
|--------|------|------|-----|
| `id` | Long | PK (자동증가) | PK, AUTO |
| `email` | String | 사용자 이메일 | UNIQUE, NOT NULL, 100자 |
| `password` | String | 암호화된 비밀번호 | NOT NULL, 255자 |
| `username` | String | 사용자명 | NOT NULL, 100자 |
| `firstName` | String | 이름 | 100자 |
| `lastName` | String | 성 | 100자 |
| `enabled` | Boolean | 계정 활성화 | NOT NULL, DEFAULT: true |
| `accountNonLocked` | Boolean | 계정 잠금 상태 | NOT NULL, DEFAULT: true |
| `credentialsNonExpired` | Boolean | 자격증명 만료 상태 | NOT NULL, DEFAULT: true |
| `accountNonExpired` | Boolean | 계정 만료 상태 | NOT NULL, DEFAULT: true |
| `twoFactorEnabled` | Boolean | 2FA 활성화 | NOT NULL, DEFAULT: false |
| `twoFactorCode` | String | 2FA 코드 | UNIQUE, 20자 |
| `twoFactorCodeExpiredAt` | LocalDateTime | 2FA 코드 만료시간 | - |
| `resetPasswordToken` | String | 비밀번호 재설정 토큰 | UNIQUE, 255자 |
| `resetPasswordTokenExpiredAt` | LocalDateTime | 토큰 만료시간 | - |
| `emailVerified` | Boolean | 이메일 검증 여부 | NOT NULL, DEFAULT: false |
| `emailVerificationToken` | String | 이메일 검증 토큰 | UNIQUE, 255자 |
| `createdAt` | LocalDateTime | 생성 시간 | NOT NULL, DEFAULT: NOW() |
| `updatedAt` | LocalDateTime | 수정 시간 | NOT NULL, DEFAULT: NOW() |

**예제 코드:**

```java
// User 생성
User user = User.builder()
    .email("user@hyfata.kr")
    .username("johndoe")
    .password(passwordEncoder.encode("SecurePassword123!"))
    .firstName("John")
    .lastName("Doe")
    .enabled(true)
    .twoFactorEnabled(false)
    .emailVerified(false)
    .build();

userRepository.save(user);  // 데이터베이스에 저장
```

**UserDetails 메서드 구현:**
```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
}

@Override
public boolean isAccountNonExpired() { return accountNonExpired; }
@Override
public boolean isAccountNonLocked() { return accountNonLocked; }
@Override
public boolean isCredentialsNonExpired() { return credentialsNonExpired; }
@Override
public boolean isEnabled() { return enabled; }
```

---

### 2. UserRepository 인터페이스 (`src/main/java/kr/hyfata/rest/api/repository/UserRepository.java`)

**역할**: 데이터 접근 객체(DAO) - Spring Data JPA를 사용한 데이터베이스 쿼리

**상속:**
```java
public interface UserRepository extends JpaRepository<User, Long>
```

- `JpaRepository<User, Long>`: User 엔티티, Long 타입의 ID
- 자동으로 기본 CRUD 메서드 제공

**커스텀 쿼리 메서드:**

| 메서드 | SQL 쿼리 | 반환 |
|--------|---------|------|
| `findByEmail(String email)` | `SELECT * FROM users WHERE email = ?` | `Optional<User>` |
| `findByUsername(String username)` | `SELECT * FROM users WHERE username = ?` | `Optional<User>` |
| `findByResetPasswordToken(String token)` | `SELECT * FROM users WHERE reset_password_token = ?` | `Optional<User>` |
| `findByEmailVerificationToken(String token)` | `SELECT * FROM users WHERE email_verification_token = ?` | `Optional<User>` |
| `existsByEmail(String email)` | `SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)` | `boolean` |
| `existsByUsername(String username)` | `SELECT EXISTS(SELECT 1 FROM users WHERE username = ?)` | `boolean` |

**제공되는 기본 메서드 (JpaRepository):**

```java
// 저장/수정
save(User user)                     // 새 사용자 저장 또는 기존 사용자 수정
saveAll(List<User> users)           // 여러 사용자 저장

// 조회
findById(Long id)                   // ID로 조회
findAll()                           // 모든 사용자 조회
findAll(Pageable pageable)          // 페이징된 조회

// 삭제
deleteById(Long id)                 // ID로 삭제
delete(User user)                   // 사용자 삭제
deleteAll()                         // 모든 사용자 삭제

// 존재 여부
existsById(Long id)                 // ID 존재 확인

// 개수
count()                             // 전체 사용자 수
```

**사용 예제:**

```java
// 1. 이메일로 사용자 찾기
Optional<User> user = userRepository.findByEmail("user@hyfata.kr");
if (user.isPresent()) {
    User foundUser = user.get();
    System.out.println(foundUser.getUsername());
}

// 2. 사용자명 존재 확인
if (userRepository.existsByUsername("johndoe")) {
    System.out.println("Username already taken");
}

// 3. 비밀번호 재설정 토큰으로 사용자 찾기
Optional<User> resetUser = userRepository.findByResetPasswordToken(resetToken);

// 4. 사용자 저장
User newUser = User.builder()
    .email("newuser@hyfata.kr")
    .username("newusername")
    .password(encodedPassword)
    .build();
userRepository.save(newUser);

// 5. 사용자 삭제
userRepository.deleteById(userId);
```

---

## 🗄️ 데이터베이스 테이블 구조

### Users 테이블 스키마

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,

    -- 2FA 필드
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    two_factor_code VARCHAR(20) UNIQUE,
    two_factor_code_expired_at TIMESTAMP,

    -- 비밀번호 재설정
    reset_password_token VARCHAR(255) UNIQUE,
    reset_password_token_expired_at TIMESTAMP,

    -- 이메일 검증
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verification_token VARCHAR(255) UNIQUE,

    -- 메타데이터
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성 (성능 최적화)
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_reset_password_token ON users(reset_password_token);
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);
CREATE INDEX idx_users_two_factor_code ON users(two_factor_code);
```

### 마이그레이션 스크립트

**파일**: `src/main/resources/db/migration/V1__create_users_table.sql`

---

## ⚙️ JPA 설정 (application.properties)

```properties
# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true
```

| 설정 | 의미 | 용도 |
|------|------|------|
| `ddl-auto=update` | 테이블 자동 업데이트 | 개발 환경 |
| `ddl-auto=validate` | 테이블 검증만 | 프로덕션 |
| `ddl-auto=create` | 테이블 생성 | 초기 설정 |
| `show-sql=true` | SQL 쿼리 출력 | 디버깅 |
| `PostgreSQLDialect` | PostgreSQL 방언 | 데이터베이스 타입 |
| `format_sql=true` | SQL 포맷팅 | 가독성 |

---

## 🔗 데이터베이스 연결 설정

### PostgreSQL 연결 정보

```properties
# Database Configuration (JPA)
spring.datasource.url=jdbc:postgresql://192.168.1.4:5432/rest_api
spring.datasource.username=postgres
spring.datasource.password=Najo$%an!2#
spring.datasource.driver-class-name=org.postgresql.Driver
```

**연결 문자열 분석:**
```
jdbc:postgresql://192.168.1.4:5432/rest_api
         ↓              ↓         ↓
      프로토콜         호스트    포트  데이터베이스명
```

---

## 🔄 데이터 흐름 예제

### 회원가입 프로세스에서의 DB 작업

```
1. 클라이언트 요청
   POST /api/auth/register
   {
     "email": "user@hyfata.kr",
     "username": "johndoe",
     "password": "SecurePassword123!"
   }

2. AuthController 수신

3. AuthService.register() 호출
   ↓
4. UserRepository.existsByEmail() 확인
   ↓ (SQL: SELECT EXISTS(SELECT 1 FROM users WHERE email = ?))

5. User 엔티티 생성
   User user = User.builder()
     .email("user@hyfata.kr")
     .username("johndoe")
     .password(BCrypt 암호화된 비밀번호)
     .emailVerificationToken(생성된 토큰)
     .build();

6. UserRepository.save(user) 호출
   ↓ (SQL: INSERT INTO users (...) VALUES (...))
   ↓ (데이터베이스 저장)

7. 이메일 발송 (비동기)
   emailService.sendEmailVerificationEmail()

8. 응답 반환
   HTTP 201 Created
   "Registration successful. Please check your email."
```

### 로그인 프로세스에서의 DB 작업

```
1. 클라이언트 요청
   POST /api/auth/login
   {
     "email": "user@hyfata.kr",
     "password": "SecurePassword123!"
   }

2. AuthService.login() 호출

3. UserRepository.findByEmail("user@hyfata.kr")
   ↓ (SQL: SELECT * FROM users WHERE email = 'user@hyfata.kr')

4. 조회된 User 객체 반환

5. BCryptPasswordEncoder.matches() 로 비밀번호 검증
   (DB의 해시된 비밀번호와 입력된 비밀번호 비교)

6. 2FA 활성화 시:
   - 2FA 코드 생성
   - UserRepository.save(user) 호출
     ↓ (SQL: UPDATE users SET two_factor_code = ?, ... WHERE id = ?)

7. JWT 토큰 생성 및 응답
```

---

## 🧪 테스트 데이터베이스

테스트 환경에서는 H2 인메모리 데이터베이스 사용:

```properties
# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
```

---

## 📈 성능 최적화

### 1. 인덱스
```sql
-- 자주 검색되는 필드에 인덱스 생성
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
```

### 2. 커버링 인덱스
```sql
-- 여러 필드로 인덱스 생성
CREATE INDEX idx_users_email_enabled ON users(email, enabled);
```

### 3. 페이징 조회
```java
// 모든 사용자 조회 (절대 금지!)
List<User> allUsers = userRepository.findAll();  // ❌

// 페이징된 조회 (권장)
Pageable pageable = PageRequest.of(0, 20);  // 첫 번째 페이지, 20개
Page<User> users = userRepository.findAll(pageable);  // ✅
```

### 4. Lazy Loading
```java
// JPA는 기본적으로 Lazy Loading 사용
// 필요할 때만 조회
```

---

## 🚨 주의사항

### 1. N+1 쿼리 문제 방지
```java
// ❌ 나쁜 예: N+1 쿼리 발생
List<User> users = userRepository.findAll();
for (User user : users) {
    // 각 user마다 추가 쿼리 발생
}

// ✅ 좋은 예: JOIN FETCH
@Query("SELECT u FROM User u LEFT JOIN FETCH u.roles")
List<User> findAllWithRoles();
```

### 2. 메모리 누수 방지
```java
// ❌ 나쁜 예: 메모리 누수
List<User> users = userRepository.findAll();  // 백만 개의 사용자 로드

// ✅ 좋은 예: 스트림 처리
userRepository.findAll().stream()
    .forEach(user -> processUser(user));
```

### 3. 동시성 제어
```java
// ❌ 나쁜 예: 경합 조건
User user = userRepository.findById(id).get();
user.setPassword(newPassword);
userRepository.save(user);

// ✅ 좋은 예: 낙관적 잠금
@Version
private Long version;
```

---

## 🔍 자주 사용되는 쿼리

### 사용자 조회

```java
// 1. 이메일로 조회
Optional<User> user = userRepository.findByEmail("user@hyfata.kr");

// 2. 사용자명으로 조회
Optional<User> user = userRepository.findByUsername("johndoe");

// 3. 모든 사용자 조회 (페이징)
Page<User> users = userRepository.findAll(PageRequest.of(0, 20));

// 4. ID로 조회
Optional<User> user = userRepository.findById(1L);
```

### 사용자 생성/수정

```java
// 1. 새 사용자 생성
User newUser = User.builder()
    .email("new@hyfata.kr")
    .password(encodedPassword)
    .build();
userRepository.save(newUser);

// 2. 기존 사용자 수정
User user = userRepository.findById(1L).get();
user.setPassword(newEncodedPassword);
userRepository.save(user);  // UPDATE 쿼리 실행
```

### 사용자 삭제

```java
// 1. ID로 삭제
userRepository.deleteById(1L);

// 2. 사용자 객체로 삭제
userRepository.delete(user);

// 3. 모든 사용자 삭제 (주의!)
userRepository.deleteAll();
```

---

## 💡 팁

1. **@Transactional**: 트랜잭션 관리 필요 시 사용
2. **Lazy Loading**: 필요한 데이터만 로드하여 성능 향상
3. **Batch Processing**: 대량 데이터 처리 시 배치 작업 사용
4. **캐싱**: 자주 조회되는 데이터는 캐시 활용
5. **로깅**: `show-sql=true`로 생성된 SQL 확인

---

## 📚 참고 자료

- [Spring Data JPA 공식 문서](https://spring.io/projects/spring-data-jpa)
- [Hibernate 공식 문서](https://hibernate.org)
- [PostgreSQL JDBC 드라이버](https://jdbc.postgresql.org)
- [Jakarta Persistence 문서](https://jakarta.ee/specifications/persistence/)
