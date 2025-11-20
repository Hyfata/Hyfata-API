# Postman Collection 빠른 시작 가이드

## 🚀 5분만에 OAuth 2.0 테스트하기

### 1️⃣ Collection Import

**Postman에서:**

1. **Collections** 아이콘 클릭 (왼쪽 사이드바)
2. **Import** 버튼 클릭
3. **File** 탭 선택
4. `OAuth2_Postman_Collection.json` 파일 선택
5. **Import** 버튼 클릭

### 2️⃣ 환경 변수 설정

Collection이 임포트되면 다음 변수들이 자동으로 로드됩니다:

| 변수 | 예시값 | 설정 방법 |
|------|-------|---------|
| `base_url` | `http://localhost:8080` | 기본값 사용 ✅ |
| `email` | `testuser@example.com` | 테스트 사용자 이메일 입력 |
| `password` | `TestPassword123!` | 테스트 사용자 비밀번호 입력 |
| `redirect_uri` | `http://localhost:3000/callback` | 필요시 수정 |
| `client_id` | *(비워둠)* | **테스트 중 자동으로 채워짐** |
| `client_secret` | *(비워둠)* | **테스트 중 자동으로 채워짐** |
| `auth_code` | *(비워둠)* | **테스트 중 자동으로 채워짐** |
| `access_token` | *(비워둠)* | **테스트 중 자동으로 채워짐** |

---

## 🎯 실행 순서

### 📌 **첫 번째 실행** (클라이언트 없을 때)

**1단계: 테스트 사용자 생성**
```
Collection → 02. User Management → Register Test User
```
- **Send** 버튼 클릭
- 응답 확인 (이메일 인증 필요)

**⚠️ 중요: 이메일 검증**

데이터베이스에서 다음 명령어 실행:
```sql
UPDATE users SET email_verified = true WHERE email = 'testuser@example.com';
```

**2단계: 클라이언트 등록**
```
Collection → 01. Client Management → Register New Client
```
- **Send** 버튼 클릭
- 응답에서 `clientId`, `clientSecret` 복사
- 환경 변수에 입력:
  - `client_id` = 복사한 clientId
  - `client_secret` = 복사한 clientSecret

### 📌 **두 번째 실행** (클라이언트 생성 후)

**3단계: Authorization Request**
```
Collection → 03. OAuth Authorization Flow → Step 1: Authorization Request (Browser)
```
- **Send** 버튼 클릭
- 또는 응답 URL을 브라우저에 복사해서 열기

**4단계: 로그인 & Authorization Code 획득**
```
Collection → 03. OAuth Authorization Flow → Step 2: Login & Get Authorization Code
```
- **Send** 버튼 클릭
- 응답 헤더의 **Location** 확인
- URL에서 `code` 파라미터 복사
- 환경 변수 `auth_code`에 입력

**5단계: Token 교환**
```
Collection → 03. OAuth Authorization Flow → Step 3: Exchange Code for Token
```
- **Send** 버튼 클릭
- 응답에서 `access_token`, `refresh_token` 복사
- 환경 변수에 입력:
  - `access_token` = 복사한 access_token
  - `refresh_token` = 복사한 refresh_token

### 📌 **세 번째 실행** (Token 획득 후)

**6단계: Protected Resource 접근**
```
Collection → 04. Token Usage & Refresh → Access Protected Resource
```
- **Send** 버튼 클릭
- 200 OK 응답 확인

**7단계: Token Refresh 테스트 (선택사항)**
```
Collection → 04. Token Usage & Refresh → Refresh Access Token
```
- **Send** 버튼 클릭
- 새로운 `access_token` 획득

---

## 🧪 테스트 케이스

에러 처리 테스트:

```
Collection → 05. Error Test Cases
```

- ❌ Invalid Client ID
- ❌ Invalid Client Secret
- ❌ Invalid Authorization Code
- ❌ Missing Authorization Code

---

## 💾 환경 변수 수동 설정

혹시 수동으로 설정해야 한다면:

1. **Environments** 아이콘 클릭
2. **Create new environment** 클릭
3. 다음 변수 추가:

```
Name: OAuth Local Testing

base_url = http://localhost:8080
email = testuser@example.com
password = TestPassword123!
redirect_uri = http://localhost:3000/callback
client_id = (빈칸)
client_secret = (빈칸)
auth_code = (빈칸)
access_token = (빈칸)
refresh_token = (빈칸)
```

---

## ⚙️ Postman 팁

### 팁 1: 응답 헤더에서 Location 확인

Step 2 (Login) 응답:
1. **Headers** 탭 클릭
2. **Location** 헤더 찾기
3. URL에서 `code` 추출
4. 환경 변수 `auth_code`에 입력

### 팁 2: 자동 리다이렉트 비활성화

Settings → General → **Automatically follow redirects** 체크 해제

이렇게 하면 리다이렉트 URL이 Response에 표시됩니다.

### 팁 3: 응답에서 값 자동 추출

Pre-request Script를 사용하여 자동 추출 가능 (고급):

```javascript
// Step 2 응답에서 code 추출
var responseUrl = pm.response.headers.get("Location");
var code = responseUrl.split("code=")[1].split("&")[0];
pm.environment.set("auth_code", code);
```

---

## 🔍 상태 확인

**현재 테스트 단계 확인:**

```
1️⃣ 테스트 사용자 생성? → 이메일 검증?
2️⃣ 클라이언트 등록? → client_id, client_secret 저장?
3️⃣ Authorization 요청? → 로그인 페이지 표시?
4️⃣ 로그인 처리? → Authorization Code 획득?
5️⃣ Token 교환? → Access Token 획득?
6️⃣ Protected Resource 접근? → 200 OK?
```

---

## 📝 체크리스트

테스트 전 확인:

- ✅ Spring Boot 애플리케이션 실행 중 (`http://localhost:8080`)
- ✅ PostgreSQL 연결됨
- ✅ Collection Import 완료
- ✅ 환경 변수 설정 완료
- ✅ 테스트 사용자 생성 및 이메일 검증 완료

---

## 🆘 문제 해결

| 문제 | 해결 |
|------|-----|
| 404 Not Found | API가 실행 중이지 않음. `./gradlew bootRun` 실행 |
| 500 Internal Server Error | 서버 로그 확인. 데이터베이스 연결 확인 |
| Invalid client | client_id가 잘못됨. 클라이언트 재등록 |
| Invalid redirect URI | redirect_uri가 등록된 것과 다름 |
| Email not verified | 데이터베이스에서 `email_verified = true`로 변경 |
| 리다이렉트 URL 안 보임 | Settings → General → Automatically follow redirects 체크 해제 |

---

## 🎓 다음 스텝

1. ✅ 전체 OAuth 플로우 테스트
2. 🔄 Multiple Clients 테스트
3. 👥 Multiple Users 테스트
4. 🔁 Token Refresh 테스트
5. ⚠️ Error Scenarios 테스트

---

## 📚 참고 자료

- [완전한 Postman 테스트 가이드](./POSTMAN_TESTING_GUIDE.md)
- [OAuth 2.0 상세 설명](./OAUTH_2_AUTHORIZATION_CODE_FLOW.md)
- [API 인증 문서](./API_AUTHENTICATION.md)

---

**모두 준비됐나요? Let's test! 🚀**