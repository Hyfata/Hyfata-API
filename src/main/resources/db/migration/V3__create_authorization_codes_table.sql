-- Create Authorization Codes Table for OAuth 2.0
CREATE TABLE IF NOT EXISTS authorization_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) UNIQUE NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    redirect_uri VARCHAR(255),
    state VARCHAR(255),
    used BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create Indexes
CREATE INDEX idx_authorization_codes_code ON authorization_codes(code);
CREATE INDEX idx_authorization_codes_client_id ON authorization_codes(client_id);
CREATE INDEX idx_authorization_codes_email ON authorization_codes(email);
CREATE INDEX idx_authorization_codes_expires_at ON authorization_codes(expires_at);
