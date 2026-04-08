-- risk_finding.category şu anda type risk_category (Postgres enum)
-- Bunu TEXT'e çeviriyoruz ki Hibernate string insert edebilsin.

ALTER TABLE risk_finding
    ALTER COLUMN category TYPE text;
