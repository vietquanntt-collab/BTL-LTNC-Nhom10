-- Upgrade databases created before item images were introduced.
-- DatabaseInitializer skips this ALTER only when the column already exists
-- (early week-5 databases included it in V001); the migration is still recorded.
ALTER TABLE items ADD COLUMN image_base64 TEXT;
