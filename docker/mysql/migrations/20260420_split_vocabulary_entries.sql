-- Split the legacy mixed vocabulary_entries table into official public entries
-- and user-owned imported entries. Run once against an existing english_nova DB.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `public_vocabulary_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phonetic` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `example_sentence` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `bnc_rank` int DEFAULT NULL,
  `frq_rank` int DEFAULT NULL,
  `wordfreq_zipf` decimal(4,2) DEFAULT NULL,
  `exchange_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `data_quality` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy',
  `audio_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `import_source` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'unknown',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_vocabulary_word` (`word`),
  KEY `idx_public_vocabulary_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_vocabulary_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `wordbook_id` bigint NOT NULL,
  `word` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phonetic` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `example_sentence` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `difficulty` int NOT NULL,
  `audio_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `import_source` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'unknown',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_wordbook_word` (`user_id`,`wordbook_id`,`word`),
  KEY `fk_user_vocabulary_entries_wordbook` (`wordbook_id`),
  KEY `idx_user_vocabulary_user_wordbook` (`user_id`,`wordbook_id`),
  KEY `idx_user_vocabulary_word` (`word`),
  CONSTRAINT `fk_user_vocabulary_entries_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_vocabulary_entries_wordbook` FOREIGN KEY (`wordbook_id`) REFERENCES `wordbooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `public_vocabulary_entries` (
  id, word, phonetic, meaning_cn, example_sentence,
  bnc_rank, frq_rank, wordfreq_zipf, exchange_info, data_quality,
  audio_url, import_source, created_at
)
SELECT
  id, word, phonetic, meaning_cn, example_sentence,
  bnc_rank, frq_rank, wordfreq_zipf, exchange_info, data_quality,
  audio_url, import_source, created_at
FROM `vocabulary_entries`
WHERE visibility = 'PUBLIC' AND import_source = 'ecdict';

INSERT IGNORE INTO `user_vocabulary_entries` (
  id, user_id, wordbook_id, word, phonetic, meaning_cn, example_sentence,
  category, difficulty, audio_url, import_source, created_at
)
SELECT
  id, user_id, wordbook_id, word, phonetic, meaning_cn, example_sentence,
  category, difficulty, audio_url, import_source, created_at
FROM `vocabulary_entries`
WHERE NOT (visibility = 'PUBLIC' AND import_source = 'ecdict');

ALTER TABLE `public_catalog_import_items` DROP FOREIGN KEY `fk_public_catalog_import_items_entry`;
ALTER TABLE `quiz_attempts` DROP FOREIGN KEY `fk_quiz_attempts_entry`;
ALTER TABLE `user_word_progress` DROP FOREIGN KEY `fk_user_word_progress_entry`;

ALTER TABLE `public_catalog_import_items`
  ADD CONSTRAINT `fk_public_catalog_import_items_entry`
  FOREIGN KEY (`entry_id`) REFERENCES `public_vocabulary_entries` (`id`) ON DELETE SET NULL;

ALTER TABLE `quiz_attempts`
  ADD CONSTRAINT `fk_quiz_attempts_entry`
  FOREIGN KEY (`vocabulary_entry_id`) REFERENCES `user_vocabulary_entries` (`id`) ON DELETE CASCADE;

ALTER TABLE `user_word_progress`
  ADD CONSTRAINT `fk_user_word_progress_entry`
  FOREIGN KEY (`vocabulary_entry_id`) REFERENCES `user_vocabulary_entries` (`id`) ON DELETE CASCADE;

UPDATE `wordbooks` w
SET word_count = (
  SELECT COUNT(*)
  FROM `user_vocabulary_entries` v
  WHERE v.wordbook_id = w.id
);

DELETE FROM `wordbooks`
WHERE user_id = 1103
  AND import_source = 'ecdict'
  AND name = 'English Nova Public Catalog';

RENAME TABLE `vocabulary_entries` TO `vocabulary_entries_legacy`;

SET FOREIGN_KEY_CHECKS = 1;
