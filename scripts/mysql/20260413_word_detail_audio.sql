INSERT INTO users (id, username, email, password_hash, status, created_at)
VALUES (1103, 'system_public_catalog', 'system-public-catalog@englishnova.local', 'SYSTEM_EXTERNAL_IMPORT', 'ACTIVE', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE username = VALUES(username), email = VALUES(email), status = VALUES(status);

SET @add_wordbooks_import_source = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'wordbooks'
              AND COLUMN_NAME = 'import_source'
        ),
        'SELECT 1',
        'ALTER TABLE wordbooks ADD COLUMN import_source varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''unknown'' AFTER source_name'
    )
);
PREPARE stmt FROM @add_wordbooks_import_source;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_vocabulary_audio = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'vocabulary_entries'
              AND COLUMN_NAME = 'audio_url'
        ),
        'SELECT 1',
        'ALTER TABLE vocabulary_entries ADD COLUMN audio_url varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER visibility'
    )
);
PREPARE stmt FROM @add_vocabulary_audio;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_vocabulary_import_source = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'vocabulary_entries'
              AND COLUMN_NAME = 'import_source'
        ),
        'SELECT 1',
        'ALTER TABLE vocabulary_entries ADD COLUMN import_source varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''unknown'' AFTER audio_url'
    )
);
PREPARE stmt FROM @add_vocabulary_import_source;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE wordbooks
SET user_id = 1103
WHERE user_id IS NULL;

UPDATE wordbooks
SET import_source = CASE
    WHEN source_name = 'FreeDictionaryAPI.com / Wiktionary' THEN 'free-dictionary-api'
    WHEN source_name = 'public-core' THEN 'public-core'
    WHEN platform = 'ANKI' THEN 'anki'
    WHEN platform = 'BAICIZHAN' THEN 'baicizhan'
    WHEN platform = 'BUBEIDANCI' THEN 'bubeidanci'
    WHEN platform = 'SHANBAY' THEN 'shanbay'
    ELSE LOWER(REPLACE(platform, '_', '-'))
END
WHERE import_source IS NULL OR import_source = '' OR import_source = 'unknown';

UPDATE vocabulary_entries
SET user_id = 1103
WHERE visibility = 'PUBLIC' AND user_id IS NULL;

UPDATE vocabulary_entries v
JOIN wordbooks w ON w.id = v.wordbook_id
SET v.import_source = w.import_source
WHERE v.import_source IS NULL OR v.import_source = '' OR v.import_source = 'unknown';

UPDATE vocabulary_entries
SET audio_url = ''
WHERE audio_url IS NULL;
