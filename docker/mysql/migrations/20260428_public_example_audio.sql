SET @schema_name = DATABASE();

SET @add_public_example_audio_url = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `public_vocabulary_entries` ADD COLUMN `example_audio_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `chinese_sentence`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'public_vocabulary_entries'
    AND column_name = 'example_audio_url'
);

PREPARE stmt FROM @add_public_example_audio_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
