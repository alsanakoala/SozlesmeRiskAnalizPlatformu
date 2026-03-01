-- risk_finding.confidence numeric -> double precision
ALTER TABLE risk_finding
    ALTER COLUMN confidence TYPE double precision;

-- risk_finding.score numeric -> double precision
ALTER TABLE risk_finding
    ALTER COLUMN score TYPE double precision;
