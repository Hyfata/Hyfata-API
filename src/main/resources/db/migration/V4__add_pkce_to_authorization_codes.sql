-- Add PKCE (Proof Key for Code Exchange) columns to authorization_codes table
-- RFC 7636: OAuth 2.0 Public Client를 위한 보안 강화

ALTER TABLE authorization_codes
ADD COLUMN code_challenge VARCHAR(500),
ADD COLUMN code_challenge_method VARCHAR(50);

-- Create index for faster queries
CREATE INDEX idx_authorization_codes_code_challenge ON authorization_codes(code_challenge);

-- Add comment for documentation
COMMENT ON COLUMN authorization_codes.code_challenge IS 'PKCE code challenge (SHA-256 hash of code_verifier, Base64URL encoded)';
COMMENT ON COLUMN authorization_codes.code_challenge_method IS 'PKCE method - S256 (recommended) or plain';
