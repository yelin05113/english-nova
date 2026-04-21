-- Add public wordbook grouping on top of public_vocabulary_entries.

CREATE TABLE IF NOT EXISTS `public_wordbooks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `license_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `license_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tag` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `word_count` int NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_wordbooks_tag` (`tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `public_wordbook_entries` (
  `public_wordbook_id` bigint NOT NULL,
  `public_entry_id` bigint NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`public_wordbook_id`,`public_entry_id`),
  UNIQUE KEY `uk_public_wordbook_entries_order` (`public_wordbook_id`,`sort_order`),
  KEY `idx_public_wordbook_entries_entry` (`public_entry_id`),
  CONSTRAINT `fk_public_wordbook_entries_entry` FOREIGN KEY (`public_entry_id`) REFERENCES `public_vocabulary_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_public_wordbook_entries_wordbook` FOREIGN KEY (`public_wordbook_id`) REFERENCES `public_wordbooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
