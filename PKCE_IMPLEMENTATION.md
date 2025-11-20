# PKCE (Proof Key for Code Exchange) 구현 가이드

## 개요

PKCE는 OAuth 2.0 Authorization Code Flow의 보안을 강화하는 메커니즘입니다. 특히 Flutter 같은 모바일 앱에서 Authorization Code Flow를 안전하게 사용할 수 있게 합니다.

**RFC 7636**: https://tools.ietf.org/html/rfc7636

## 구현된 컴포넌트

### 1. PkceUtil (서버 측)
- **파일**: `src/main/java/kr/hyfata/rest/api/util/PkceUtil.java`
- **역할**: PKCE 검증 및 code challenge 생성
- **주요 메서드**:
  - `generateCodeChallenge(codeVerifier)`: SHA-256 해시 + Base64URL 인코딩
  - `verifyCodeChallenge(codeVerifier, storedCodeChallenge)`: code_verifier 검증
  - `isValidCodeVerifier(codeVerifier)`: code_verifier 유효성 검증

### 2. AuthorizationCode 엔티티
- **추가 필드**:
  - `codeChallenge`: PKCE code challenge (500자)
  - `codeChallengeMethod`: PKCE 메서드 (S256, plain)

### 3. OAuthService/OAuthServiceImpl
- **오버로드된 메서드**:
  - `generateAuthorizationCode(..., codeChallenge, codeChallengeMethod)`
  - `exchangeCodeForToken(..., codeVerifier)`

### 4. OAuthController
- **authorize 엔드포인트**: `code_challenge`, `code_challenge_method` 파라미터 수락
- **login 엔드포인트**: PKCE 파라미터 전달
- **token 엔드포인트**: `code_verifier` 파라미터 수락

### 5. 데이터베이스 마이그레이션
- **파일**: `src/main/resources/db/migration/V4__add_pkce_to_authorization_codes.sql`
- **변경사항**: authorization_codes 테이블에 PKCE 컬럼 추가

---

## OAuth 2.0 + PKCE 플로우

```
┌─────────────┐                    ┌─────────────┐                ┌─────────────┐
│ Flutter App │                    │   Browser   │                │ REST API    │
│  (Client)   │                    │  (User)     │                │  (Server)   │
└─────────────┘                    └─────────────┘                └─────────────┘
      │                                   │                               │
      ├─ 1. Generate code_verifier ─────>│                               │
      │                                   │                               │
      ├─ 2. Generate code_challenge ────>│                               │
      │    (SHA-256 hash)                 │                               │
      │                                   │                               │
      ├─ 3. Authorization Request ──────────────────────────────────────>│
      │    (code_challenge, code_challenge_method)                       │
      │                                   │                               │
      │                                   ├─ 4. Show login page ────────>│
      │                                   │<─ Return login page ─────────┤
      │                                   │                               │
      │                                   ├─ 5. Submit credentials ─────>│
      │                                   │     (code_challenge)          │
      │                                   │<─ Redirect with code ────────┤
      │                                   │    (code, state)              │
      │<─ 6. Redirect Callback ─────────┤                               │
      │     (code, state)                 │                               │
      │                                   │                               │
      ├─ 7. Token Exchange ──────────────────────────────────────────────>│
      │    (code, code_verifier)          │                               │
      │                                   │     ├─ Verify code_verifier ─┤
      │                                   │     │ (SHA-256 hash)          │
      │                                   │     ├─ Compare with          │
      │                                   │     │ code_challenge          │
      │<─ 8. Return tokens ───────────────────────────────────────────────┤
      │    (access_token, refresh_token)  │                               │
      │                                   │                               │
```

---

## Flutter 앱에서 사용 방법

### 1단계: Code Verifier 생성
```dart
import 'dart:math';
import 'package:crypto/crypto.dart';
import 'dart:convert';

// 1. Code Verifier 생성 (43-128자 사이의 임의 문자열)
String _generateCodeVerifier() {
  const String charset =
    'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  final random = Random.secure();
  final codeVerifier = List<int>.generate(128, (_) {
    return charset.codeUnitAt(random.nextInt(charset.length));
  }).join();
  return codeVerifier;
}

String codeVerifier = _generateCodeVerifier();
```

### 2단계: Code Challenge 생성
```dart
// 2. Code Challenge 생성 (SHA-256 해시 + Base64URL 인코딩)
String _generateCodeChallenge(String codeVerifier) {
  final bytes = utf8.encode(codeVerifier);
  final digest = sha256.convert(bytes);
  final hash = base64Url.encode(digest.bytes).replaceAll('=', '');
  return hash;
}

String codeChallenge = _generateCodeChallenge(codeVerifier);
```

### 3단계: Authorization Request
```dart
// 3. Authorization Request (웹뷰 또는 브라우저에서)
const String authorizationEndpoint = 'https://api.hyfata.com/oauth/authorize';

final params = {
  'client_id': 'client_001',
  'redirect_uri': 'https://site1.com/callback',
  'response_type': 'code',
  'state': _generateState(), // CSRF 방지
  'code_challenge': codeChallenge,
  'code_challenge_method': 'S256', // 권장
};

final authorizationUrl = Uri.parse(authorizationEndpoint)
  .replace(queryParameters: params);

// 웹뷰 또는 System browser에서 오픈
```

### 4단계: Authorization Code 수신
```dart
// 4. Redirect callback에서 Authorization Code 수신
// redirect_uri?code=xxx&state=xxx
String authorizationCode = params['code'];
String state = params['state'];

// State 파라미터 검증
if (state != storedState) {
  throw Exception('State mismatch - CSRF attack');
}
```

### 5단계: Token Exchange (PKCE 포함)
```dart
// 5. Token Exchange - code_verifier 필수
final tokenEndpoint = Uri.parse('https://api.hyfata.com/oauth/token');

final response = await http.post(
  tokenEndpoint,
  headers: {'Content-Type': 'application/x-www-form-urlencoded'},
  body: {
    'grant_type': 'authorization_code',
    'code': authorizationCode,
    'client_id': 'client_001',
    'client_secret': 'secret_001',
    'redirect_uri': 'https://site1.com/callback',
    'code_verifier': codeVerifier, // PKCE: 클라이언트만 알고 있는 값
  },
);

if (response.statusCode == 200) {
  final tokenData = json.decode(response.body);
  String accessToken = tokenData['access_token'];
  String refreshToken = tokenData['refresh_token'];

  // 토큰 저장 (안전한 저장소에)
  await _storage.write(key: 'access_token', value: accessToken);
  await _storage.write(key: 'refresh_token', value: refreshToken);
} else {
  throw Exception('Token exchange failed: ${response.body}');
}
```

---

## PKCE 없이 OAuth 2.0 사용 (호환성)

기존 OAuth 2.0 Authorization Code Flow도 계속 지원됩니다:

### 서버 측
```java
// PKCE 파라미터 없이도 작동
String authCode = oAuthService.generateAuthorizationCode(
    clientId, email, redirectUri, state);

OAuthTokenResponse tokenResponse = oAuthService.exchangeCodeForToken(
    code, clientId, clientSecret, redirectUri);
```

### 클라이언트 측
```dart
// code_challenge, code_verifier 파라미터 없음
final params = {
  'client_id': 'client_001',
  'redirect_uri': 'https://site1.com/callback',
  'response_type': 'code',
  'state': _generateState(),
};

// Token exchange에서 code_verifier 생략
final response = await http.post(
  tokenEndpoint,
  body: {
    'grant_type': 'authorization_code',
    'code': authorizationCode,
    'client_id': 'client_001',
    'client_secret': 'secret_001',
    'redirect_uri': 'https://site1.com/callback',
    // code_verifier 없음
  },
);
```

---

## 보안 특징

### 1. Authorization Code 탈취 방지
- Authorization Code만 탈취되어도 PKCE 없이는 토큰을 얻을 수 없음
- code_verifier는 클라이언트에서만 관리되며 네트워크를 통해 전송되지 않음 (첫 번째 요청)

### 2. HTTPS만 가능
- Authorization Request와 Token Exchange 모두 HTTPS 필수
- 특히 웹뷰 또는 System Browser를 통해 보안 채널 보장

### 3. State 파라미터 (CSRF 방지)
- PKCE와 함께 State 파라미터도 검증
- 이중 보안: PKCE + State

### 4. Code Challenge 저장
- code_challenge는 서버에 저장됨
- code_verifier는 클라이언트에서만 관리되며, Token Exchange 시에만 전송

---

## 테스트 가이드

### curl을 사용한 테스트

#### 1. Code Verifier 생성 (Bash)
```bash
#!/bin/bash

# Generate 128-character code verifier
CODE_VERIFIER=$(head -c 96 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=')
echo "Code Verifier: $CODE_VERIFIER"

# Generate code challenge (SHA-256 + Base64URL)
CODE_CHALLENGE=$(echo -n "$CODE_VERIFIER" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')
echo "Code Challenge: $CODE_CHALLENGE"
```

#### 2. Authorization Request
```bash
curl -X GET "https://api.hyfata.com/oauth/authorize" \
  -G \
  --data-urlencode "client_id=client_001" \
  --data-urlencode "redirect_uri=https://site1.com/callback" \
  --data-urlencode "response_type=code" \
  --data-urlencode "state=random_state_123" \
  --data-urlencode "code_challenge=$CODE_CHALLENGE" \
  --data-urlencode "code_challenge_method=S256"
```

#### 3. Authorization Code 수신 (redirect_uri?code=xxx&state=xxx)
```bash
# 위 요청에서 반환된 authorization code를 저장
AUTHORIZATION_CODE="<code_from_redirect>"
STATE="random_state_123"
```

#### 4. Token Exchange (PKCE 포함)
```bash
curl -X POST "https://api.hyfata.com/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=$AUTHORIZATION_CODE" \
  -d "client_id=client_001" \
  -d "client_secret=secret_001" \
  -d "redirect_uri=https://site1.com/callback" \
  -d "code_verifier=$CODE_VERIFIER"
```

---

## API 엔드포인트

### GET /oauth/authorize (1단계)
Authorization Request를 처리하고 로그인 페이지를 반환합니다.

**파라미터**:
```
client_id (필수): OAuth 클라이언트 ID
redirect_uri (필수): 콜백 URL
response_type (필수): "code"
state (선택): CSRF 방지용 임의 문자열
code_challenge (선택): PKCE code challenge
code_challenge_method (선택): PKCE 메서드 ("S256" 권장)
```

**응답**:
- 성공: 로그인 HTML 페이지
- 실패: 에러 페이지

---

### POST /oauth/login (2단계)
사용자 자격증명을 검증하고 Authorization Code를 생성합니다.

**파라미터**:
```
email (필수): 사용자 이메일
password (필수): 사용자 비밀번호
client_id (필수): OAuth 클라이언트 ID
redirect_uri (필수): 콜백 URL
state (필수): CSRF 방지용 파라미터
code_challenge (선택): PKCE code challenge
code_challenge_method (선택): PKCE 메서드
```

**응답**:
- 성공: redirect_uri?code=xxx&state=xxx로 리다이렉트
- 실패: 에러 메시지와 함께 로그인 페이지 재표시

---

### POST /oauth/token (3단계)
Authorization Code를 Token으로 교환합니다.

**파라미터**:
```
grant_type (필수): "authorization_code"
code (필수): Authorization Code
client_id (필수): OAuth 클라이언트 ID
client_secret (필수): OAuth 클라이언트 Secret
redirect_uri (필수): 콜백 URL
code_verifier (선택): PKCE code verifier
```

**응답**:
```json
{
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 86400000,
  "scope": "user:email user:profile"
}
```

**에러 응답**:
```json
{
  "error": "invalid_grant",
  "error_description": "code_verifier verification failed"
}
```

---

## 주요 변경사항

### 1. AuthorizationCode 엔티티
```java
@Column(length = 500)
private String codeChallenge;  // PKCE code challenge

@Column(length = 50)
private String codeChallengeMethod;  // PKCE method
```

### 2. OAuthServiceImpl
- `generateAuthorizationCode(..., codeChallenge, codeChallengeMethod)`
- `exchangeCodeForToken(..., codeVerifier)` with PKCE validation

### 3. OAuthController
- authorize: `code_challenge`, `code_challenge_method` 파라미터 수락
- login: PKCE 파라미터 전달
- token: `code_verifier` 파라미터 수락

### 4. 데이터베이스
- V4 마이그레이션: authorization_codes 테이블에 PKCE 컬럼 추가

---

## 참고 자료

- **RFC 7636 - PKCE**: https://tools.ietf.org/html/rfc7636
- **OAuth 2.0 Implicit Flow 취약점**: https://tools.ietf.org/html/draft-ietf-oauth-browser-based-apps
- **Google OAuth 2.0 PKCE 구현**: https://developers.google.com/identity/protocols/oauth2/native-app
- **Flutter oauth2 패키지**: https://pub.dev/packages/oauth2
- **Flutter webview_flutter**: https://pub.dev/packages/webview_flutter
