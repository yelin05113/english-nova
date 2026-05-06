SET @schema_name = DATABASE();

SET @add_public_corrected_english = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `public_vocabulary_entries` ADD COLUMN `corrected_english` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `example_sentence`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'public_vocabulary_entries'
    AND column_name = 'corrected_english'
);

PREPARE stmt FROM @add_public_corrected_english;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_public_chinese_sentence = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `public_vocabulary_entries` ADD COLUMN `chinese_sentence` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `corrected_english`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'public_vocabulary_entries'
    AND column_name = 'chinese_sentence'
);

PREPARE stmt FROM @add_public_chinese_sentence;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_user_corrected_english = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user_vocabulary_entries` ADD COLUMN `corrected_english` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `example_sentence`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'user_vocabulary_entries'
    AND column_name = 'corrected_english'
);

PREPARE stmt FROM @add_user_corrected_english;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_user_chinese_sentence = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user_vocabulary_entries` ADD COLUMN `chinese_sentence` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `corrected_english`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'user_vocabulary_entries'
    AND column_name = 'chinese_sentence'
);

PREPARE stmt FROM @add_user_chinese_sentence;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `example_enrichment_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entry_id` bigint NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `last_error` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `locked_at` timestamp NULL DEFAULT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_example_enrichment_tasks_entry` (`entry_type`,`entry_id`),
  KEY `idx_example_enrichment_tasks_status` (`status`,`updated_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
