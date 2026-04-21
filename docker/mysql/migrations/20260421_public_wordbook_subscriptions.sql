-- Replace copied public wordbooks with user subscriptions and public-progress tables.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `user_public_wordbooks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `public_wordbook_id` bigint NOT NULL,
  `current_sort_order` int NOT NULL DEFAULT '0',
  `completed_count` int NOT NULL DEFAULT '0',
  `wrong_count` int NOT NULL DEFAULT '0',
  `subscribed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_studied_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_public_wordbook` (`user_id`,`public_wordbook_id`),
  KEY `idx_user_public_wordbooks_user` (`user_id`,`last_studied_at` DESC),
  CONSTRAINT `fk_user_public_wordbooks_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_public_wordbooks_wordbook` FOREIGN KEY (`public_wordbook_id`) REFERENCES `public_wordbooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_public_wordbook_wrong_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `public_wordbook_id` bigint NOT NULL,
  `public_entry_id` bigint NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_public_wordbook_wrong_entry` (`user_id`,`public_wordbook_id`,`public_entry_id`),
  KEY `idx_user_public_wordbook_wrong_wordbook` (`user_id`,`public_wordbook_id`),
  KEY `fk_user_public_wrong_entry` (`public_entry_id`),
  CONSTRAINT `fk_user_public_wrong_entry` FOREIGN KEY (`public_entry_id`) REFERENCES `public_vocabulary_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_public_wrong_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_public_wrong_wordbook` FOREIGN KEY (`public_wordbook_id`) REFERENCES `public_wordbooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `quiz_sessions` DROP FOREIGN KEY `fk_quiz_sessions_wordbook`;
ALTER TABLE `quiz_sessions` DROP INDEX `fk_quiz_sessions_wordbook`;
ALTER TABLE `quiz_sessions`
  CHANGE COLUMN `wordbook_id` `target_id` bigint NOT NULL,
  ADD COLUMN `target_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER_WORDBOOK' AFTER `user_id`,
  ADD COLUMN `start_offset` int NOT NULL DEFAULT '0' AFTER `mode`,
  ADD KEY `idx_quiz_sessions_target` (`user_id`,`target_type`,`target_id`,`status`);

ALTER TABLE `quiz_attempts` DROP FOREIGN KEY `fk_quiz_attempts_entry`;
ALTER TABLE `quiz_attempts` DROP INDEX `uk_quiz_attempts_session_entry`;
ALTER TABLE `quiz_attempts` DROP INDEX `fk_quiz_attempts_entry`;
ALTER TABLE `quiz_attempts`
  CHANGE COLUMN `vocabulary_entry_id` `user_vocabulary_entry_id` bigint DEFAULT NULL,
  ADD COLUMN `public_entry_id` bigint DEFAULT NULL AFTER `user_vocabulary_entry_id`,
  ADD COLUMN `wrong_submissions` int NOT NULL DEFAULT '0' AFTER `correct_option`,
  ADD KEY `fk_quiz_attempts_user_entry` (`user_vocabulary_entry_id`),
  ADD KEY `fk_quiz_attempts_public_entry` (`public_entry_id`);
ALTER TABLE `quiz_attempts`
  ADD CONSTRAINT `fk_quiz_attempts_user_entry` FOREIGN KEY (`user_vocabulary_entry_id`) REFERENCES `user_vocabulary_entries` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_quiz_attempts_public_entry` FOREIGN KEY (`public_entry_id`) REFERENCES `public_vocabulary_entries` (`id`) ON DELETE CASCADE;

UPDATE `quiz_sessions`
SET `target_type` = 'USER_WORDBOOK',
    `start_offset` = 0
WHERE `target_type` IS NULL OR `target_type` = '';

CREATE TEMPORARY TABLE `tmp_copied_public_wordbooks`
SELECT `id`
FROM `wordbooks`
WHERE `platform` = 'ECDICT'
  AND (
    `source_name` LIKE 'ECDICT Public Wordbook - %'
    OR (`import_source` = 'ecdict' AND `source_name` LIKE 'ECDICT Public Wordbook%')
  );

DELETE qa
FROM `quiz_attempts` qa
JOIN `quiz_sessions` qs ON qs.`id` = qa.`session_id`
JOIN `tmp_copied_public_wordbooks` t ON t.`id` = qs.`target_id`
WHERE qs.`target_type` = 'USER_WORDBOOK';

DELETE qs
FROM `quiz_sessions` qs
JOIN `tmp_copied_public_wordbooks` t ON t.`id` = qs.`target_id`
WHERE qs.`target_type` = 'USER_WORDBOOK';

DELETE p
FROM `user_word_progress` p
JOIN `user_vocabulary_entries` v ON v.`id` = p.`vocabulary_entry_id`
JOIN `tmp_copied_public_wordbooks` t ON t.`id` = v.`wordbook_id`;

DELETE v
FROM `user_vocabulary_entries` v
JOIN `tmp_copied_public_wordbooks` t ON t.`id` = v.`wordbook_id`;

DELETE w
FROM `wordbooks` w
JOIN `tmp_copied_public_wordbooks` t ON t.`id` = w.`id`;

DROP TEMPORARY TABLE `tmp_copied_public_wordbooks`;

SET FOREIGN_KEY_CHECKS = 1;
