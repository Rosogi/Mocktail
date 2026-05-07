ALTER TABLE mock_definitions
    ADD COLUMN IF NOT EXISTS request_match_mode VARCHAR(20) NOT NULL DEFAULT 'basic',
    ADD COLUMN IF NOT EXISTS request_match_groups TEXT;

UPDATE mock_definitions
SET request_match_mode = 'basic'
WHERE request_match_mode IS NULL OR request_match_mode = '';
