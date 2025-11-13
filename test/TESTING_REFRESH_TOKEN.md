# 토큰 갱신 (Refresh Token) 테스트 가이드

이 문서는 Hyfata REST API의 토큰 갱신(Refresh Token) 기능을 테스트하는 방법을 설명합니다.

## 개요

Refresh Token을 사용하여 만료된 Access Token을 새로 발급받을 수 있습니다.

- **Access Token 유효기간**: 24시간
- **Refresh Token 유효기간**: 7일
- **사용 시점**: Access Token 만료 후 또는 만료 전 사전 갱신

---

## 선행 조건

1. 애플리케이션 실행
```bash
./gradlew bootRun
```

2. 테스트용 사용자 계정 준비
   - 로그인 가능한 계정 필요
   - 이메일 검증 완료 필요

3. 유효한 Refresh Token 필요
   - 로그인 시 받은 refreshToken

---

## 테스트 1: 토큰 갱신 (정상)

### 요청
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }'
```

### 예상 응답 (200 OK)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "twoFactorRequired": false,
  "message": "Login successful"
}
```

### 테스트 케이스

#### 1.1 유효한 Refresh Token으로 갱신
- **전제조건**: 유효한 refreshToken 필요
- **요청**: refreshToken 포함한 갱신 요청
- **예상 결과**: 200 상태 코드, 새로운 토큰 쌍 반환
- **검증사항**:
  - 새 accessToken이 이전과 다름
  - 새 refreshToken이 이전과 다름 (또는 갱신 정책에 따라 동일할 수 있음)
  - tokenType = "Bearer"
  - expiresIn = 86400000ms (24시간)
  - 새 accessToken으로 보호된 엔드포인트 접근 가능

#### 1.2 여러 번 연속 갱신
- **요청**: 첫 번째 갱신 후 새 refreshToken으로 다시 갱신
- **예상 결과**: 200 상태 코드, 계속해서 토큰 갱신 가능
- **검증사항**:
  - 매번 새로운 토큰 쌍 발급됨
  - 이전 토큰으로는 더 이상 접근 불가능

---

## 테스트 2: 토큰 갱신 (실패 케이스)

### 2.1 만료된 Refresh Token

#### 요청
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "expired-token"
  }'
```

#### 예상 응답 (401 Unauthorized)
```json
{
  "message": "Invalid or expired refresh token"
}
```

#### 테스트 방법
- 7일 이상 경과한 refreshToken으로 요청
- 또는 환경변수에서 `jwt.refresh-expiration` 값을 작게 설정하여 빠르게 만료시킨 후 테스트

### 2.2 유효하지 않은 Refresh Token

#### 요청
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "invalid-token-format"
  }'
```

#### 예상 응답 (401 Unauthorized)
```json
{
  "message": "Invalid refresh token"
}
```

### 2.3 Refresh Token 없음

#### 요청
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{}'
```

#### 예상 응답 (400 Bad Request)
```json
{
  "message": "Refresh token is required"
}
```

### 2.4 손상된 Refresh Token

#### 요청
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.corrupted.signature"
  }'
```

#### 예상 응답 (401 Unauthorized)
```json
{
  "message": "Invalid refresh token"
}
```

### 2.5 잘못된 시그니처의 Refresh Token

#### 요청
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNjc0NDQ3ODAwLCJleHAiOjE2NzQ1MzQyMDB9.invalidsignature"
  }'
```

#### 예상 응답 (401 Unauthorized)
```json
{
  "message": "Invalid refresh token"
}
```

---

## 통합 테스트 시나리오

### 시나리오 1: 로그인 → 토큰 갱신 → 보호된 엔드포인트 접근
1. 로그인하여 accessToken, refreshToken 획득
2. 즉시 갱신 요청 (accessToken이 아직 유효한 상태)
3. 새 accessToken 획득
4. 보호된 엔드포인트에서 새 accessToken으로 접근 확인
5. 이전 accessToken으로 접근 시도 (실패 확인)

### 시나리오 2: Access Token 만료 후 갱신
1. 로그인하여 accessToken, refreshToken 획득
2. Access Token 만료 시간까지 대기 (테스트 환경에서는 `jwt.expiration` 값 단축)
3. 만료된 accessToken으로 요청 시도 (401 에러 확인)
4. refreshToken으로 갱신
5. 새 accessToken으로 요청 성공 확인

### 시나리오 3: Refresh Token 만료
1. 로그인하여 accessToken, refreshToken 획득
2. Refresh Token 만료 시간까지 대기 (테스트 환경에서는 `jwt.refresh-expiration` 값 단축)
3. refreshToken으로 갱신 시도 (401 에러 확인)
4. 다시 로그인 필요

---

## 자동화 테스트 (cURL 스크립트)

### refresh_token_test.sh
```bash
#!/bin/bash

API_URL="http://localhost:8080"
EMAIL="testuser@example.com"
PASSWORD="TestPassword123!"

# 1. 로그인하여 토큰 획득
echo "=== 1단계: 로그인 ==="
LOGIN_RESPONSE=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"$PASSWORD\"}")

echo "로그인 응답:"
echo $LOGIN_RESPONSE | jq '.'

ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.accessToken')
REFRESH_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.refreshToken')

echo ""
echo "AccessToken: $ACCESS_TOKEN"
echo "RefreshToken: $REFRESH_TOKEN"

# 2. 보호된 엔드포인트 접근 (현재 accessToken 사용)
echo ""
echo "=== 2단계: 현재 AccessToken으로 보호된 엔드포인트 접근 ==="
PROTECTED_RESPONSE=$(curl -s -X GET $API_URL/api/protected/user-info \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "응답: $PROTECTED_RESPONSE"

# 3. 토큰 갱신
echo ""
echo "=== 3단계: Refresh Token으로 갱신 ==="
REFRESH_RESPONSE=$(curl -s -X POST $API_URL/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")

echo "갱신 응답:"
echo $REFRESH_RESPONSE | jq '.'

NEW_ACCESS_TOKEN=$(echo $REFRESH_RESPONSE | jq -r '.accessToken')
NEW_REFRESH_TOKEN=$(echo $REFRESH_RESPONSE | jq -r '.refreshToken')

echo ""
echo "새 AccessToken: $NEW_ACCESS_TOKEN"
echo "새 RefreshToken: $NEW_REFRESH_TOKEN"

# 4. 새 AccessToken으로 보호된 엔드포인트 접근
echo ""
echo "=== 4단계: 새 AccessToken으로 보호된 엔드포인트 접근 ==="
NEW_PROTECTED_RESPONSE=$(curl -s -X GET $API_URL/api/protected/user-info \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN")

echo "응답: $NEW_PROTECTED_RESPONSE"

# 5. 이전 AccessToken으로 접근 시도 (실패 예상)
echo ""
echo "=== 5단계: 이전 AccessToken으로 접근 시도 (실패 예상) ==="
OLD_TOKEN_RESPONSE=$(curl -s -X GET $API_URL/api/protected/user-info \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "응답 (401 에러 예상): $OLD_TOKEN_RESPONSE"

# 6. 유효하지 않은 refreshToken으로 갱신 시도
echo ""
echo "=== 6단계: 유효하지 않은 RefreshToken으로 갱신 시도 (실패 예상) ==="
INVALID_REFRESH=$(curl -s -X POST $API_URL/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"invalid-token\"}")

echo "응답 (401 에러 예상): $INVALID_REFRESH"
```

사용법:
```bash
chmod +x refresh_token_test.sh
./refresh_token_test.sh
```

---

## Postman 컬렉션

### 토큰 갱신
```
Method: POST
URL: http://localhost:8080/api/auth/refresh
Headers:
  - Content-Type: application/json
Body (JSON):
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## 테스트 환경 설정 (빠른 만료 테스트용)

`application.properties` 또는 `application-test.properties`에서 다음 값을 단축:

```properties
# 테스트용: 1시간 (3600000ms)
jwt.expiration=3600000

# 테스트용: 2시간 (7200000ms)
jwt.refresh-expiration=7200000
```

이렇게 설정하면 토큰 만료를 더 빠르게 테스트할 수 있습니다.

---

## 성능 테스트

### 동시 갱신 요청
```bash
#!/bin/bash

API_URL="http://localhost:8080"
REFRESH_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# 10개의 동시 요청 발송
for i in {1..10}; do
  curl -X POST $API_URL/api/auth/refresh \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}" &
done

wait
echo "모든 요청 완료"
```

---

## 보안 고려사항

1. **토큰 저장**: Refresh Token은 안전한 저장소에 보관 (localStorage보다 HttpOnly Cookie 권장)
2. **토큰 전송**: HTTPS를 통해서만 전송
3. **토큰 만료**: Refresh Token도 만료되므로 주기적인 재인증 필요
4. **토큰 로테이션**: 각 갱신 시 새로운 Refresh Token 발급 고려
5. **CORS 설정**: 불필요한 출처 접근 차단

---

## 주의사항

1. **연쇄적 갱신**: Refresh Token으로 계속 갱신할 수 있으므로 만료 체크 필수
2. **클라이언트 동기화**: 여러 탭/창에서 동시에 토큰 갱신 시 동기화 이슈 주의
3. **로깅**: 민감한 토큰 정보는 로그에 남기지 않음
4. **시스템 시간**: 서버와 클라이언트의 시간 차이로 인한 문제 확인

---

## 문제 해결

### Refresh Token 갱신 계속 실패
- JWT Secret 설정 확인 (application.properties)
- 토큰 형식 정확성 확인
- 토큰 만료 여부 확인 (7일 초과 시 새 로그인 필요)

### Access Token으로는 보호된 엔드포인트 접근 실패
- JWT 설정 및 서명 알고리즘 확인
- 토큰의 클레임 정보 확인 (subject, issued-at, expiration)

### 갱신 후에도 이전 토큰이 유효함
- 토큰 폐기(blacklist) 메커니즘 미구현 확인
- 필요시 Redis 등을 사용하여 폐기 목록 관리

