-- Change clients.owner_email to clients.owner_id (FK to users)
-- Note: This migration assumes the previous owner_email column may have existing data.
-- If migrating from owner_email strings, a data migration script would be needed first.

-- Drop old owner_email column and index if they exist
ALTER TABLE clients DROP COLUMN IF EXISTS owner_email;
DROP INDEX IF EXISTS idx_clients_owner_email;

-- Add owner_id foreign key column
ALTER TABLE clients ADD COLUMN IF NOT EXISTS owner_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

-- Create index on owner_id
CREATE INDEX IF NOT EXISTS idx_clients_owner_id ON clients(owner_id);
