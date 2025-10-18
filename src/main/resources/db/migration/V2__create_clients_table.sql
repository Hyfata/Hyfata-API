-- Create OAuth Clients Table
CREATE TABLE IF NOT EXISTS clients (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(100) UNIQUE NOT NULL,
    client_secret VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    frontend_url VARCHAR(255) NOT NULL,
    redirect_uris TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_tokens_per_user INTEGER NOT NULL DEFAULT 5,
    owner_email VARCHAR(255),

    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create Indexes
CREATE INDEX idx_clients_client_id ON clients(client_id);
CREATE INDEX idx_clients_enabled ON clients(enabled);
CREATE INDEX idx_clients_owner_email ON clients(owner_email);
