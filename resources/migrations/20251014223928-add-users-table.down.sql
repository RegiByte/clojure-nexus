DROP TRIGGER IF EXISTS trg_nexus_users_updated_at ON nexus.users;
--;;
DROP FUNCTION IF EXISTS nexus.update_updated_at();
--;;
DROP INDEX IF EXISTS idx_nexus_users_email;
--;;
DROP TABLE IF EXISTS nexus.users;