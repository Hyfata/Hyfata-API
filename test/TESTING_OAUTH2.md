# OAuth 2.0 Authorization Code Flow 테스트 가이드

이 문서는 Hyfata REST API의 OAuth 2.0 Authorization Code Flow 구현을 테스트하는 방법을 설명합니다.

## 개요

OAuth 2.0은 제3자 애플리케이션(클라이언트)이 사용자의 리소스에 안전하게 접근할 수 있도록 하는 표준 프로토콜입니다.

- **구현 방식**: Authorization Code Flow
- **3단계 프로세스**: Authorization → Login → Token Exchange
- **보안**: State 파라미터로 CSRF 공격 방지, 클라이언트 시크릿으로 백엔드 검증

---

## 선행 조건

1. 애플리케이션 실행
```bash
./gradlew bootRun
```

2. OAuth 클라이언트 등록
   - client_id: 클라이언트 식별자
   - client_secret: 클라이언트 시크릿 (백엔드에서만 사용)
   - redirect_uri: 리다이렉트 URL (클라이언트 콜백 주소)

3. 테스트용 사용자 계정
   - 이메일 검증 완료한 계정

4. 관련 문서 확인
   - OAUTH_2_AUTHORIZATION_CODE_FLOW.md
   - OAUTH_IMPLEMENTATION_SUMMARY.md

---

## OAuth 2.0 Authorization Code Flow 프로세스

```
┌─────────┐                          ┌──────────────────┐
│ Browser │                          │  Authorization   │
│ Client  │                          │     Server       │
└────┬────┘                          └─────────┬────────┘
     │                                         │
     │ 1. GET /oauth/authorize?               │
     │    client_id=xxx&redirect_uri=xxx       │
     ├────────────────────────────────────────→│
     │                                         │
     │ 2. 로그인 페이지 표시                    │
     │←────────────────────────────────────────┤
     │                                         │
     │ 3. POST /oauth/login (credentials)      │
     ├────────────────────────────────────────→│
     │                                         │
     │ 4. Authorization Code 생성 및 리다이렉트 │
     │←─── redirect_uri?code=xxx&state=xxx ────┤
     │                                         │
     │ (백엔드에서)                            │
     │ 5. POST /oauth/token                    │
     │    code=xxx&client_secret=xxx           │
     ├────────────────────────────────────────→│
     │                                         │
     │ 6. Access Token & Refresh Token 반환    │
     │←────────────────────────────────────────┤
```

---

## 테스트 1: Authorization 요청 (1단계)

### 요청
```bash
curl -X GET "http://localhost:8080/oauth/authorize?client_id=client_001&redirect_uri=https://site1.com/callback&state=random_state_123&response_type=code"
```

### 또는 브라우저에서
```
http://localhost:8080/oauth/authorize?client_id=client_001&redirect_uri=https://site1.com/callback&state=random_state_123
```

### 예상 응답
- 로그인 페이지 (HTML 렌더링)
- Thymeleaf 템플릿: oauth/login

### 테스트 케이스

#### 1.1 정상 authorization 요청
- **입력**: 유효한 client_id, redirect_uri, state
- **예상 결과**: 200 상태 코드, 로그인 페이지 표시
- **검증사항**:
  - 로그인 폼 표시됨
  - client_id, redirect_uri, state가 폼에 숨겨진 필드로 포함
  - 이메일, 비밀번호 입력 필드 표시

#### 1.2 유효하지 않은 클라이언트
- **입력**: 등록되지 않은 client_id
- **예상 결과**: 에러 페이지 표시
- **응답**: "Invalid client" 에러 메시지

#### 1.3 유효하지 않은 redirect_uri
- **입력**: 클라이언트에 등록되지 않은 redirect_uri
- **예상 결과**: 에러 페이지 표시
- **응답**: "Invalid redirect URI" 에러 메시지

#### 1.4 response_type이 code가 아님
- **입력**: response_type=token 또는 다른 값
- **예상 결과**: 에러 페이지 표시
- **응답**: "Unsupported response_type. Only 'code' is supported"

#### 1.5 필수 파라미터 누락
- **입력**: client_id 또는 redirect_uri 누락
- **예상 결과**: 400 또는 에러 페이지

---

## 테스트 2: 로그인 및 Authorization Code 생성 (2단계)

### 요청 (Form Data)
```bash
curl -X POST http://localhost:8080/oauth/login \
  -d "email=testuser@example.com&password=TestPassword123!&client_id=client_001&redirect_uri=https://site1.com/callback&state=random_state_123"
```

### 예상 응답 (302 Redirect)
```
Location: https://site1.com/callback?code=authorization_code_xyz&state=random_state_123
```

### 테스트 케이스

#### 2.1 정상 로그인 및 Authorization Code 생성
- **전제조건**: 이메일 검증 완료한 계정
- **입력**: 정확한 이메일, 비밀번호, 클라이언트 정보
- **예상 결과**: 302 리다이렉트, code와 state 포함
- **검증사항**:
  - Authorization Code 생성됨 (UUID 형식)
  - state 파라미터가 그대로 반환됨 (CSRF 방지)
  - code는 단일 사용만 가능 (일회성)

#### 2.2 잘못된 비밀번호
- **입력**: 정확한 이메일, 틀린 비밀번호
- **예상 결과**: 로그인 페이지 재표시, 에러 메시지
- **응답**: "Invalid email or password"

#### 2.3 이메일 검증 미완료
- **입력**: 검증되지 않은 이메일로 로그인
- **예상 결과**: 로그인 페이지 재표시, 에러 메시지
- **응답**: "Email verification required"

#### 2.4 비활성화된 계정
- **입력**: 비활성화된 계정으로 로그인
- **예상 결과**: 로그인 페이지 재표시, 에러 메시지
- **응답**: "Account is disabled"

---

## 테스트 3: Token Exchange (3단계)

### 요청 (Form-encoded)
```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=authorization_code_xyz&client_id=client_001&client_secret=secret_001&redirect_uri=https://site1.com/callback"
```

### 예상 응답 (200 OK)
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 86400000,
  "scope": "user:email user:profile"
}
```

### 테스트 케이스

#### 3.1 정상 token exchange
- **전제조건**: 2단계에서 획득한 유효한 authorization code
- **입력**: 정확한 code, client_id, client_secret, redirect_uri
- **예상 결과**: 200 상태 코드, access_token & refresh_token 반환
- **검증사항**:
  - access_token이 유효한 JWT 형식
  - refresh_token이 유효한 JWT 형식
  - token_type = "Bearer"
  - expires_in = 86400000ms (24시간)

#### 3.2 유효하지 않은 authorization code
- **입력**: 잘못된 또는 만료된 code
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "invalid_grant",
  "error_description": "Authorization code is invalid or expired"
}
```

#### 3.3 잘못된 client_secret
- **입력**: 정확한 code, 틀린 client_secret
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "invalid_client",
  "error_description": "Client secret is invalid"
}
```

#### 3.4 redirect_uri 불일치
- **입력**: 1단계에서 사용한 redirect_uri와 다른 값
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "invalid_grant",
  "error_description": "Redirect URI does not match"
}
```

#### 3.5 grant_type이 authorization_code가 아님
- **입력**: grant_type=implicit 또는 다른 값
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "unsupported_grant_type",
  "error_description": "Only 'authorization_code' is supported"
}
```

#### 3.6 필수 파라미터 누락
- **입력**: code, client_id, 또는 client_secret 누락
- **예상 결과**: 400 상태 코드
- **응답**:
```json
{
  "error": "invalid_request",
  "error_description": "Required parameter is missing"
}
```

#### 3.7 이미 사용된 authorization code (일회성 테스트)
- **전제조건**: 같은 code로 2번 token exchange 시도
- **첫 번째 요청**: 성공 (access_token 획득)
- **두 번째 요청**: 400 상태 코드
- **응답**:
```json
{
  "error": "invalid_grant",
  "error_description": "Authorization code has already been used"
}
```

---

## 통합 테스트 시나리오

### 시나리오 1: 완전한 OAuth 2.0 플로우 (브라우저)
1. 브라우저에서 Authorization 요청
   - GET /oauth/authorize?client_id=client_001&redirect_uri=https://site1.com/callback&state=abc123
2. 로그인 페이지에서 로그인
   - email, password 입력 및 제출
3. Authorization Code 수신
   - 리다이렉트: https://site1.com/callback?code=auth_code_xyz&state=abc123
4. 백엔드에서 Token Exchange
   - POST /oauth/token with code, client_secret 등
5. Access Token 획득
   - 앞으로 API 호출 시 Bearer 토큰 사용

### 시나리오 2: State 파라미터 검증 (CSRF 방지)
1. 1단계에서 state="abc123" 전송
2. 2단계 리다이렉트 응답에서 state="abc123" 반환 확인
3. 클라이언트가 저장한 state와 비교하여 일치 확인

### 시나리오 3: Authorization Code 일회성 테스트
1. 2단계에서 authorization code 획득
2. 3단계에서 code로 token exchange (성공)
3. 같은 code로 다시 token exchange 시도 (실패)

### 시나리오 4: 보안 검증
1. 다른 클라이언트의 코드 사용 불가능 확인
2. 잘못된 client_secret 사용 불가능 확인
3. redirect_uri 변조 불가능 확인

---

## 자동화 테스트 (cURL 스크립트)

### oauth_flow_test.sh
```bash
#!/bin/bash

API_URL="http://localhost:8080"
CLIENT_ID="client_001"
CLIENT_SECRET="secret_001"
REDIRECT_URI="https://site1.com/callback"
EMAIL="testuser@example.com"
PASSWORD="TestPassword123!"
STATE="random_state_$(date +%s)"

echo "=== OAuth 2.0 Authorization Code Flow 테스트 ==="
echo ""

# 1. Authorization 요청
echo "=== 1단계: Authorization 요청 ==="
AUTH_REQUEST_URL="$API_URL/oauth/authorize?client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&state=$STATE&response_type=code"
echo "URL: $AUTH_REQUEST_URL"
echo ""

# 2. 로그인 및 Authorization Code 생성
echo "=== 2단계: 로그인 및 Authorization Code 생성 ==="
LOGIN_RESPONSE=$(curl -s -i -X POST $API_URL/oauth/login \
  -d "email=$EMAIL&password=$PASSWORD&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&state=$STATE")

# Location 헤더에서 code 추출
AUTH_CODE=$(echo "$LOGIN_RESPONSE" | grep -oP 'code=\K[^&]+' | head -1)
RETURNED_STATE=$(echo "$LOGIN_RESPONSE" | grep -oP 'state=\K[^&\s]+' | head -1)

echo "Authorization Code: $AUTH_CODE"
echo "Returned State: $RETURNED_STATE"
echo "State 검증: $([ "$STATE" = "$RETURNED_STATE" ] && echo 'OK' || echo 'FAILED')"
echo ""

# 3. Token Exchange
echo "=== 3단계: Token Exchange ==="
TOKEN_RESPONSE=$(curl -s -X POST $API_URL/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=$AUTH_CODE&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&redirect_uri=$REDIRECT_URI")

echo "Token 응답:"
echo $TOKEN_RESPONSE | jq '.'

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token // "failed"')
REFRESH_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.refresh_token // "failed"')

echo ""
echo "Access Token: $ACCESS_TOKEN"
echo "Refresh Token: $REFRESH_TOKEN"

# 4. Access Token으로 보호된 엔드포인트 접근
if [ "$ACCESS_TOKEN" != "failed" ]; then
  echo ""
  echo "=== 4단계: Access Token으로 보호된 엔드포인트 접근 ==="
  PROTECTED_RESPONSE=$(curl -s -X GET $API_URL/api/protected/user-info \
    -H "Authorization: Bearer $ACCESS_TOKEN")

  echo "보호된 리소스 접근 응답:"
  echo $PROTECTED_RESPONSE | jq '.'
fi

# 5. 이미 사용된 Authorization Code로 재시도 (실패 예상)
echo ""
echo "=== 5단계: 이미 사용된 Authorization Code로 재시도 (실패 예상) ==="
REUSE_RESPONSE=$(curl -s -X POST $API_URL/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=$AUTH_CODE&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&redirect_uri=$REDIRECT_URI")

echo "응답 (에러 예상):"
echo $REUSE_RESPONSE | jq '.'
```

사용법:
```bash
chmod +x oauth_flow_test.sh
./oauth_flow_test.sh
```

---

## Postman 컬렉션

### 1단계: Authorization 요청
```
Method: GET
URL: http://localhost:8080/oauth/authorize
Params:
  - client_id: client_001
  - redirect_uri: https://site1.com/callback
  - state: random_state_123
  - response_type: code
```

### 2단계: 로그인
```
Method: POST
URL: http://localhost:8080/oauth/login
Headers:
  - Content-Type: application/x-www-form-urlencoded
Body:
  - email: testuser@example.com
  - password: TestPassword123!
  - client_id: client_001
  - redirect_uri: https://site1.com/callback
  - state: random_state_123
```

### 3단계: Token Exchange
```
Method: POST
URL: http://localhost:8080/oauth/token
Headers:
  - Content-Type: application/x-www-form-urlencoded
Body:
  - grant_type: authorization_code
  - code: authorization_code_xyz
  - client_id: client_001
  - client_secret: secret_001
  - redirect_uri: https://site1.com/callback
```

---

## OAuth 클라이언트 설정 예시

### 클라이언트 등록 (데이터베이스)

예시 데이터:
```sql
INSERT INTO oauth_clients (client_id, client_secret, redirect_uris, scope, created_at) VALUES
('client_001', 'secret_001', 'https://site1.com/callback', 'user:email user:profile', NOW()),
('client_002', 'secret_002', 'https://site2.com/callback,https://site2.com/callback2', 'user:email', NOW());
```

---

## 보안 고려사항

1. **Client Secret 보안**: 절대 프론트엔드에 노출되지 않아야 함 (백엔드만 사용)
2. **State 파라미터**: CSRF 공격 방지, 무작위 값 사용
3. **Redirect URI 화이트리스트**: 정확히 일치해야 함
4. **HTTPS 사용**: 프로덕션에서는 필수
5. **Authorization Code 일회성**: 재사용 불가능하게 구현
6. **토큰 만료**: 충분히 짧은 시간 (24시간 권장)
7. **CORS 설정**: 필요한 출처만 허용

---

## 주의사항

1. **클라이언트 등록**: 먼저 클라이언트를 등록해야 함
2. **State 파라미터**: 보안을 위해 무조건 포함해야 함
3. **HTTPS**: 프로덕션에서는 필수 (토큰이 URL 및 헤더에 포함)
4. **타임아웃**: 각 단계의 타임아웃 설정 필요
5. **로깅**: 민감한 정보(secret, token) 로그 미출력

---

## 문제 해결

### Authorization 요청 실패 - "Invalid client"
- client_id가 정확한지 확인
- 클라이언트가 등록되어 있는지 확인

### Authorization 요청 실패 - "Invalid redirect URI"
- 클라이언트에 등록된 redirect_uri와 정확히 일치하는지 확인
- URL 인코딩 문제 확인

### 로그인 실패
- 이메일 주소 정확성 확인
- 비밀번호 정확성 확인
- 이메일 검증 완료 확인

### Token Exchange 실패 - "invalid_grant"
- Authorization Code 유효성 확인
- Code 유효기간 확인 (보통 10분)
- redirect_uri 일치 확인
- Client Secret 정확성 확인

### 보호된 리소스 접근 실패
- Access Token 유효성 확인
- 토큰 만료 여부 확인
- Authorization 헤더 형식 확인 ("Bearer {token}")

---

## 추가 기능 고려사항

### Refresh Token을 사용한 토큰 갱신
```bash
POST /oauth/token
grant_type=refresh_token&refresh_token=xxx&client_id=xxx&client_secret=xxx
```

### Authorization Code 수명 단축
보안을 위해 1회용 코드의 유효기간을 10분 이내로 설정하는 것이 권장됨

### 다중 Redirect URI 지원
같은 클라이언트에 여러 콜백 주소 등록 가능 (쉼표로 구분)

