# 🚀 Postman으로 OAuth 2.0 테스트하기

Hyfata REST API의 OAuth 2.0 Authorization Code Flow를 Postman에서 테스트하기 위한 완벽한 가이드입니다.

---

## 📦 제공 파일

### 1. **OAuth2_Postman_Collection.json**
   - Postman Collection 파일
   - 모든 엔드포인트가 미리 구성됨
   - 환경 변수 자동 설정
   - **이 파일을 Postman에 Import 하세요!**

### 2. **POSTMAN_QUICK_START.md** ⭐ **여기서 시작하세요!**
   - 5분 빠른 시작 가이드
   - 순서대로 따라가기만 하면 됩니다
   - 체크리스트 포함

### 3. **POSTMAN_TESTING_GUIDE.md**
   - 상세한 설명 문서
   - 각 단계별 상세 설명
   - 트러블슈팅 가이드
   - 심화 내용

---

## ⚡ 5분 시작 가이드

### Step 1: Collection Import (1분)

```
Postman → Collections → Import
       → OAuth2_Postman_Collection.json 선택
       → Import 클릭
```

### Step 2: 테스트 사용자 생성 (1분)

```
Collection → 02. User Management → Register Test User → Send
```

**⚠️ 이메일 검증 필수!**
```sql
UPDATE users SET email_verified = true
WHERE email = 'testuser@example.com';
```

### Step 3: 클라이언트 등록 (1분)

```
Collection → 01. Client Management → Register New Client → Send
```

응답에서:
- `clientId` 복사 → 환경변수 `client_id`에 붙여넣기
- `clientSecret` 복사 → 환경변수 `client_secret`에 붙여넣기

### Step 4: OAuth 플로우 테스트 (2분)

```
Collection → 03. OAuth Authorization Flow
         → Step 1: Authorization Request → Send
         → Step 2: Login & Get Authorization Code → Send
         → Step 3: Exchange Code for Token → Send
```

**완료! 🎉 Access Token 획득!**

---

## 🎯 테스트 플로우

```
1️⃣ Register Test User
   ↓
2️⃣ Register Client (clientId, clientSecret 획득)
   ↓
3️⃣ Step 1: Authorization Request (로그인 페이지 표시)
   ↓
4️⃣ Step 2: Login (Authorization Code 획득)
   ↓
5️⃣ Step 3: Exchange Code for Token (Access Token 획득)
   ↓
✅ 성공! Token 사용 가능
```

---

## 📊 환경 변수 자동 채우기

테스트를 진행하면서 다음 변수들이 자동으로 채워집니다:

| 단계 | 변수 | 획득 방법 |
|------|------|---------|
| 클라이언트 등록 | `client_id` | Register New Client 응답 |
| 클라이언트 등록 | `client_secret` | Register New Client 응답 |
| 로그인 처리 | `auth_code` | Step 2 응답의 Location 헤더 |
| Token 교환 | `access_token` | Step 3 응답 |
| Token 교환 | `refresh_token` | Step 3 응답 |

---

## 🔍 각 단계 설명

### 📌 Step 1: Authorization Request
```
GET /oauth/authorize?client_id=XXX&redirect_uri=XXX&state=XXX
```
**결과:** 로그인 페이지 표시

### 📌 Step 2: Login & Get Code
```
POST /oauth/login
email=testuser@example.com
password=TestPassword123!
client_id=XXX
redirect_uri=XXX
state=XXX
```
**결과:** Authorization Code 발급 (10분 유효)

### 📌 Step 3: Exchange Code for Token
```
POST /oauth/token
grant_type=authorization_code
code=XXX
client_id=XXX
client_secret=XXX
redirect_uri=XXX
```
**결과:** Access Token + Refresh Token

---

## 🧪 테스트 시나리오

### 시나리오 1: 기본 플로우 (필수)
- ✅ 클라이언트 등록
- ✅ 사용자 로그인
- ✅ Token 교환
- ✅ Protected Resource 접근

### 시나리오 2: 여러 클라이언트 (선택)
- 클라이언트 A로 테스트
- 클라이언트 B로 테스트
- 동시 토큰 관리 확인

### 시나리오 3: 에러 처리 (선택)
```
Collection → 05. Error Test Cases
```
- Invalid Client ID
- Invalid Client Secret
- Invalid Authorization Code
- Missing Parameters

### 시나리오 4: Token Refresh (선택)
```
Collection → 04. Token Usage & Refresh → Refresh Access Token
```

---

## ✅ 체크리스트

테스트 시작 전:

- [ ] Spring Boot 애플리케이션 실행 중 (`./gradlew bootRun`)
- [ ] PostgreSQL 연결됨
- [ ] Postman 설치됨
- [ ] `OAuth2_Postman_Collection.json` Import 완료
- [ ] 환경 변수 설정 완료

테스트 중:

- [ ] 테스트 사용자 생성
- [ ] 이메일 검증 (데이터베이스 UPDATE)
- [ ] 클라이언트 등록
- [ ] Authorization 요청
- [ ] 로그인 처리
- [ ] Token 교환
- [ ] Protected Resource 접근

---

## 🔐 보안 포인트

### Authorization Code (Step 2)
- **유효 기간:** 10분
- **일회용:** 한 번만 사용 가능
- **생성:** 로그인 시마다 새로 생성

### Access Token (Step 3)
- **유효 기간:** 24시간
- **사용:** `Authorization: Bearer {token}`
- **만료 후:** Refresh Token으로 재발급

### Refresh Token (Step 3)
- **유효 기간:** 7일
- **사용:** 새로운 Access Token 발급
- **보관:** 안전하게 저장 필수

### CSRF 방지
- **State 파라미터:** 모든 요청에 포함
- **값:** 임의의 문자열 (추천: 난수)
- **검증:** 응답의 State와 요청의 State 비교

---

## 🆘 자주 묻는 질문

### Q1: "Invalid client" 에러가 뜹니다
**A:** 클라이언트 ID가 잘못되었거나 존재하지 않습니다.
- 클라이언트 등록 요청 다시 실행
- 응답의 clientId 정확히 복사
- 환경 변수에서 `client_id` 확인

### Q2: "Invalid redirect URI" 에러가 뜹니다
**A:** 요청의 redirect_uri가 등록된 것과 다릅니다.
- 클라이언트 등록 시 설정한 redirectUris 확인
- 모든 요청에서 동일한 redirect_uri 사용
- 필요하면 새 클라이언트 등록

### Q3: "Invalid email or password" 에러가 뜹니다
**A:** 사용자가 없거나 비밀번호가 틀렸습니다.
- 테스트 사용자 등록 요청 다시 실행
- 비밀번호 정확성 확인
- 이메일 검증 여부 확인

### Q4: Step 2에서 리다이렉트 URL이 보이지 않습니다
**A:** Postman이 자동으로 리다이렉트를 따라갑니다.
- Settings → General → "Automatically follow redirects" 체크 해제
- Response → Headers 탭에서 Location 확인
- 또는 Response Body에서 리다이렉트 URL 확인

### Q5: Authorization Code가 만료되었습니다
**A:** Authorization Code는 10분만 유효합니다.
- Step 2 (로그인)를 다시 실행
- 새로운 code 획득
- Step 3에서 새 code 사용

---

## 📚 추가 문서

- **[POSTMAN_QUICK_START.md](./POSTMAN_QUICK_START.md)** - 5분 시작 가이드
- **[POSTMAN_TESTING_GUIDE.md](./POSTMAN_TESTING_GUIDE.md)** - 상세 테스트 가이드
- **[OAUTH_2_AUTHORIZATION_CODE_FLOW.md](./OAUTH_2_AUTHORIZATION_CODE_FLOW.md)** - OAuth 2.0 이론
- **[API_AUTHENTICATION.md](./API_AUTHENTICATION.md)** - JWT 인증 API

---

## 🚀 다음 단계

1. ✅ Postman에서 OAuth 2.0 테스트
2. 🔄 프론트엔드에서 실제 구현
3. 📱 모바일 앱에서 테스트
4. 🔐 프로덕션 배포 준비

---

## 💡 팁과 트릭

### 팁 1: 환경 변수 효율적으로 사용
- 변수를 설정하면 `{{변수명}}`으로 자동 치환
- 모든 요청에서 재사용 가능
- 쉽게 여러 환경 전환 가능

### 팁 2: Postman Pre-request Script
고급 사용자를 위해 자동 응답 추출 가능:

```javascript
// Step 2 응답에서 code 자동 추출
var location = pm.response.headers.get("Location");
var code = new URLSearchParams(new URL(location).search).get("code");
pm.environment.set("auth_code", code);
```

### 팁 3: Collection 공유
- Collection을 팀과 공유 가능
- 모두 동일한 환경에서 테스트 가능
- Postman Workspace에서 협업 가능

---

## 📞 문제 발생 시

1. [POSTMAN_TESTING_GUIDE.md](./POSTMAN_TESTING_GUIDE.md)의 **트러블슈팅** 섹션 참조
2. 서버 로그 확인: `./gradlew bootRun` 출력
3. 데이터베이스 상태 확인: PostgreSQL 연결 확인
4. Postman 캐시 초기화: Settings → Clear Cache

---

## 📝 마지막 체크리스트

모든 테스트가 성공했나요?

- [ ] ✅ 테스트 사용자 생성
- [ ] ✅ 클라이언트 등록
- [ ] ✅ Authorization 요청
- [ ] ✅ 로그인 처리
- [ ] ✅ Authorization Code 획득
- [ ] ✅ Token 교환
- [ ] ✅ Access Token 획득
- [ ] ✅ Protected Resource 접근

**모두 완료되면 OAuth 2.0이 정상 작동하는 것입니다! 🎉**

---

**프로덕션 배포 전에 [완전한 가이드](./OAUTH_2_AUTHORIZATION_CODE_FLOW.md)를 꼭 읽으세요!**

---

**마지막 업데이트:** 2024-11-05