DO $$
BEGIN
    -- kolon zaten varsa dokunma
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name='risk_finding'
          AND column_name='mitigation'
    ) THEN
        ALTER TABLE risk_finding
        ADD COLUMN mitigation TEXT;
    END IF;
END $$;
