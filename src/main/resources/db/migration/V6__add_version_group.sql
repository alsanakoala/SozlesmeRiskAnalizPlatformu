-- Belge versiyonlarını gruplayabilmek için version_group_id ekliyoruz
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name='document'
          AND column_name='version_group_id'
    ) THEN
        ALTER TABLE document
        ADD COLUMN version_group_id UUID;
    END IF;
END $$;

-- Eski kayıtlar için varsayılan: kendi id'si
UPDATE document
SET version_group_id = id
WHERE version_group_id IS NULL;
