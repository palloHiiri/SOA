-- Create application databases in the default PostgreSQL database.
-- The PostgreSQL container runs this script only when its data directory
-- is initialized for the first time.
SELECT 'CREATE DATABASE sso_ident OWNER postgres'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'sso_ident')\gexec

SELECT 'CREATE DATABASE keto OWNER postgres'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keto')\gexec

SELECT 'CREATE DATABASE inventory OWNER postgres'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'inventory')\gexec

SELECT 'CREATE DATABASE clients OWNER postgres'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'clients')\gexec

SELECT 'CREATE DATABASE tickets OWNER postgres'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tickets')\gexec

SELECT 'CREATE DATABASE booking OWNER postgres'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'booking')\gexec
