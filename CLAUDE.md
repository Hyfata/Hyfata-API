# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Hyfata REST API** - A Spring Boot 3.4.4-based REST API using Java 17, built with Gradle. The project implements a layered architecture (Controller → Service → Repository) with a PostgreSQL database backend.

**Current Features**:
- Complete JWT-based authentication system
- BCrypt password encryption
- 2-factor authentication (email-based)
- Token refresh mechanism
- Password reset functionality
- Email verification
- Spring Security integration

## Common Development Commands

### Build Commands
```bash
# Build the project
./gradlew build

# Build without running tests
./gradlew build -x test

# Clean build artifacts
./gradlew clean

# Build and display dependency tree
./gradlew dependencies
```

### Running the Application
```bash
# Run the application
./gradlew bootRun

# Run with specific profile (if configured)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Testing
```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests HyfataRestApiApplicationTests

# Run tests with output details
./gradlew test --info

# Run tests and generate HTML report
./gradlew test

# Run tests without stopping on failure (continue on error)
./gradlew test --continue
```

### Code Quality and Compilation
```bash
# Compile only (no tests)
./gradlew compileJava

# Check for compilation errors
./gradlew classes

# Verify build without full test suite
./gradlew check -x test
```

### Gradle Wrapper
- Use `./gradlew` on macOS/Linux
- Use `gradlew.bat` on Windows
- The wrapper ensures consistent Gradle version (7.6.1 or similar) across all environments

## Codebase Architecture

### Layer Structure

**Controller Layer** → `src/main/java/kr/hyfata/rest/api/controller/`
- HTTP request handlers using Spring MVC annotations
- Currently: `FirstController.java` - Simple `GET /first` endpoint
- Pattern: Constructor injection with `@RequiredArgsConstructor` (Lombok)

**Service Layer** → `src/main/java/kr/hyfata/rest/api/service/`
- Business logic and service interfaces
- Structure: Interface (`FirstService.java`) + Implementation (`impl/FirstServiceImpl.java`)
- Currently returns hardcoded test data from a HashMap
- Pattern: Interface-based design for abstraction and testability

**Repository Layer** → `src/main/java/kr/hyfata/rest/api/repository/`
- Database access layer (currently empty placeholder)
- Future implementations will use one of the configured database technologies

### Database Architecture

The project is configured with **multiple database access technologies**:

1. **R2DBC (PRIMARY)** - Reactive database connectivity for async operations
   - Connection: `r2dbc:pool:postgresql://192.168.1.4:5432/rest_api`
   - Credentials in `src/main/resources/application.properties`

2. **JPA** - Object-Relational Mapping
   - For ORM-based entity mapping

3. **JDBC** - Low-level SQL execution
   - Direct database operations

4. **MyBatis** - SQL Mapper Framework
   - For custom SQL mapping (test support included)

**Note:** Currently, none of these are actively used; service layer returns in-memory test data. When adding real database functionality, choose the appropriate technology based on needs:
- Use **R2DBC** for reactive/async operations
- Use **JPA** for simple ORM-based development
- Use **MyBatis** for complex SQL queries

### Application Entry Point

**HyfataRestApiApplication.java** - `@SpringBootApplication` main class
- Enables auto-configuration, component scanning, and configuration properties
- Auto-scans packages under `kr.hyfata.rest.api`
- Configuration loaded from `src/main/resources/application.properties`

### Test Structure

**Location:** `src/test/java/kr/hyfata/rest/api/`

**Current State:**
- Minimal coverage: Only `HyfataRestApiApplicationTests.java` with a basic context load test
- Test framework: JUnit 5 (Jupiter)
- Spring integration: `@SpringBootTest` for full application context
- Available: Reactor test utilities for reactive components

**Future Testing:**
- Add unit tests for services with mocks
- Add integration tests for controllers
- Add repository tests with embedded database or test containers

## Key Dependencies and Tools

| Component | Details |
|-----------|---------|
| **Spring Boot** | 3.4.4 with auto-configuration |
| **Java** | Version 17 (via toolchain) |
| **Build Tool** | Gradle (wrapper included) |
| **ORM/DB Access** | R2DBC, JPA, JDBC, MyBatis |
| **Database** | PostgreSQL 12+ |
| **Utility** | Lombok (boilerplate reduction) |
| **Testing** | JUnit 5, Spring Test, Reactor Test |

## Important Configuration Details

### Java Version
- **Target:** Java 17 (specified in `build.gradle`)
- When adding dependencies, ensure compatibility with Java 17

### Build Configuration
- **Dependency Management:** Spring Dependency Management 1.1.7 (manages transitive dependencies)
- **Spring Boot Plugin:** 3.4.4 (handles packaging and deployment)

### Application Properties
Located in `src/main/resources/application.properties`:
```properties
spring.application.name=Hyfata-RestAPI
spring.r2dbc.url=r2dbc:pool:postgresql://192.168.1.4:5432/rest_api
spring.r2dbc.username=postgres
spring.r2dbc.password=Najo$%an!2#
```

## Development Patterns

### Service Implementation Pattern
Services follow the interface-based pattern for loose coupling:
```
FirstService (interface)
  └─ FirstServiceImpl (@Service implementing FirstService)
```

When adding new services:
1. Create interface: `src/main/java/kr/hyfata/rest/api/service/YourService.java`
2. Create implementation: `src/main/java/kr/hyfata/rest/api/service/impl/YourServiceImpl.java`
3. Inject interface type into controllers/other services

### Dependency Injection
- Uses constructor injection via `@RequiredArgsConstructor` (Lombok)
- Example: Fields are final and immutable, preventing accidental mutation

### REST Endpoint Pattern
- Controllers use `@RestController` with method-level `@GetMapping`, `@PostMapping`, etc.
- Automatic JSON serialization via Spring (returns Map, objects, or ResponseEntity)

## Directory Structure Reference

```
Hyfata-RestAPI/
├── src/main/java/kr/hyfata/rest/api/
│   ├── HyfataRestApiApplication.java        # Application entry point
│   ├── controller/
│   │   └── FirstController.java             # REST endpoints
│   ├── service/
│   │   ├── FirstService.java                # Service interface
│   │   └── impl/
│   │       └── FirstServiceImpl.java         # Service implementation
│   └── repository/                          # Database layer (placeholder)
├── src/main/resources/
│   └── application.properties                # Configuration
├── src/test/java/kr/hyfata/rest/api/
│   └── HyfataRestApiApplicationTests.java   # Basic tests
├── build.gradle                             # Gradle build config
├── gradlew / gradlew.bat                    # Gradle wrapper (use these!)
└── HELP.md                                  # Spring Boot reference
```

## When Adding New Features

1. **New REST Endpoint:** Add method to controller or create new controller class
2. **New Business Logic:** Add service interface and implementation
3. **Database Access:** Implement repository interface (use R2DBC or JPA based on use case)
4. **Testing:** Add corresponding unit/integration tests in `src/test/`
5. **Configuration:** Update `application.properties` if needed

## Authentication System (Recently Implemented)

### Key Components
- **User Entity** (`src/main/java/kr/hyfata/rest/api/entity/User.java`)
  - Implements Spring Security's UserDetails interface
  - JPA entity with security fields (2FA, password reset, email verification)

- **Authentication Service** (`src/main/java/kr/hyfata/rest/api/service/impl/AuthServiceImpl.java`)
  - Handles registration, login, 2FA verification, password reset
  - Manages token lifecycle with refresh mechanism

- **JWT Utility** (`src/main/java/kr/hyfata/rest/api/util/JwtUtil.java`)
  - Token generation and validation using JJWT 0.12.3
  - HS256 signing algorithm

- **Security Configuration** (`src/main/java/kr/hyfata/rest/api/config/SecurityConfig.java`)
  - BCrypt password encoder
  - JWT filter integration
  - Public vs protected endpoint configuration

### Protected Endpoints
All endpoints under `/api/protected/**` require valid JWT in Authorization header:
```
Authorization: Bearer <access_token>
```

### Testing Authentication
- JWT utility tests: **8 tests, all passing**
- Use `./gradlew test --tests "*JwtUtilTest*"` to run

### For More Information
- See `API_AUTHENTICATION.md` for complete API documentation
- See `AUTHENTICATION_IMPLEMENTATION.md` for implementation details

## Notes for Future Development

- **Validation:** Add `@Valid` and JSR-303 annotations for request validation
- **API Documentation:** Add SpringDoc OpenAPI for Swagger UI
- **Rate Limiting:** Implement login attempt rate limiting
- **Audit Logging:** Implement audit logs for security events
- **Refresh Token Rotation:** Consider refresh token rotation for enhanced security
