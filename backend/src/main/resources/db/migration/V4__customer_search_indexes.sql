-- Trigram indexes make "contains" searches (LIKE '%oak%') fast: a normal index only
-- helps with "starts with". pg_trgm ships with PostgreSQL.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Expressions match the queries in CustomerSearch exactly, so PostgreSQL can use them
CREATE INDEX customer_name_trgm_idx  ON customer USING gin (lower(name) gin_trgm_ops);
CREATE INDEX customer_phone_trgm_idx ON customer USING gin (phone gin_trgm_ops);
CREATE INDEX property_street_trgm_idx ON property USING gin (lower(street) gin_trgm_ops);
