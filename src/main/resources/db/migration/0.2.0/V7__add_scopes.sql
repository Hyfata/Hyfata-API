-- OAuth 2.0 Scope 지원을 위한 컬럼 추가

-- 클라이언트 기본/최대 scope
ALTER TABLE clients ADD COLUMN IF NOT EXISTS default_scopes VARCHAR(500);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS allowed_scopes VARCHAR(500);

-- Authorization Code에 승인 scope 저장
ALTER TABLE authorization_codes ADD COLUMN IF NOT EXISTS scopes VARCHAR(500);

-- 세션별 발급 scope 기록
ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS scopes VARCHAR(500);
