CREATE TABLE IF NOT EXISTS nexus.users (
    id SERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    middle_name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
--;;
CREATE INDEX IF NOT EXISTS idx_nexus_users_email ON nexus.users (email);
--;;
CREATE OR REPLACE FUNCTION nexus.update_updated_at()
RETURNS TRIGGER AS $func$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$func$ LANGUAGE plpgsql;
--;;
CREATE TRIGGER trg_nexus_users_updated_at
BEFORE UPDATE ON nexus.users
FOR EACH ROW 
EXECUTE FUNCTION nexus.update_updated_at();