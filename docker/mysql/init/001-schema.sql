
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `import_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `import_tasks` (
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `wordbook_id` bigint DEFAULT NULL,
  `platform` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `estimated_cards` int NOT NULL,
  `imported_cards` int NOT NULL DEFAULT '0',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `queued_at` timestamp NOT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  `error_message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `queue_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`task_id`),
  KEY `fk_import_tasks_wordbook` (`wordbook_id`),
  KEY `idx_import_tasks_user_queued` (`user_id`,`queued_at` DESC),
  CONSTRAINT `fk_import_tasks_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_import_tasks_wordbook` FOREIGN KEY (`wordbook_id`) REFERENCES `wordbooks` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `public_catalog_import_jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_catalog_import_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_words` int NOT NULL DEFAULT '0',
  `processed_words` int NOT NULL DEFAULT '0',
  `imported_words` int NOT NULL DEFAULT '0',
  `updated_words` int NOT NULL DEFAULT '0',
  `skipped_words` int NOT NULL DEFAULT '0',
  `failed_words` int NOT NULL DEFAULT '0',
  `refresh_existing` tinyint(1) NOT NULL DEFAULT '0',
  `batch_size` int NOT NULL DEFAULT '150',
  `started_at` timestamp NULL DEFAULT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `error_message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_public_catalog_import_jobs_status` (`status`,`created_at`),
  KEY `fk_public_catalog_import_jobs_user` (`created_by_user_id`),
  CONSTRAINT `fk_public_catalog_import_jobs_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `public_catalog_import_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_catalog_import_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL,
  `word` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `last_error` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entry_id` bigint DEFAULT NULL,
  `has_phonetic` tinyint(1) NOT NULL DEFAULT '0',
  `has_meaning_cn` tinyint(1) NOT NULL DEFAULT '0',
  `has_example` tinyint(1) NOT NULL DEFAULT '0',
  `has_category` tinyint(1) NOT NULL DEFAULT '0',
  `has_audio` tinyint(1) NOT NULL DEFAULT '0',
  `has_definition` tinyint(1) NOT NULL DEFAULT '0',
  `has_frequency` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_catalog_import_items_job_word` (`job_id`,`word`),
  KEY `idx_public_catalog_import_items_status` (`job_id`,`status`,`id`),
  KEY `fk_public_catalog_import_items_entry` (`entry_id`),
  CONSTRAINT `fk_public_catalog_import_items_entry` FOREIGN KEY (`entry_id`) REFERENCES `vocabulary_entries` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_public_catalog_import_items_job` FOREIGN KEY (`job_id`) REFERENCES `public_catalog_import_jobs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `quiz_attempts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_attempts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `vocabulary_entry_id` bigint NOT NULL,
  `prompt_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_text` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_a` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_b` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_c` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_d` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `correct_option` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `selected_option` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_correct` tinyint(1) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `answered_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_attempts_session_entry` (`session_id`,`vocabulary_entry_id`),
  KEY `fk_quiz_attempts_entry` (`vocabulary_entry_id`),
  KEY `idx_quiz_attempts_session_answered` (`session_id`,`answered_at`),
  KEY `idx_quiz_attempts_user_created` (`user_id`,`created_at` DESC),
  CONSTRAINT `fk_quiz_attempts_entry` FOREIGN KEY (`vocabulary_entry_id`) REFERENCES `vocabulary_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_quiz_attempts_session` FOREIGN KEY (`session_id`) REFERENCES `quiz_sessions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_quiz_attempts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `quiz_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_sessions` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `wordbook_id` bigint NOT NULL,
  `mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_questions` int NOT NULL,
  `answered_questions` int NOT NULL DEFAULT '0',
  `correct_answers` int NOT NULL DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at` timestamp NOT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_quiz_sessions_wordbook` (`wordbook_id`),
  KEY `idx_quiz_sessions_user_started` (`user_id`,`started_at` DESC),
  CONSTRAINT `fk_quiz_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_quiz_sessions_wordbook` FOREIGN KEY (`wordbook_id`) REFERENCES `wordbooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `study_agenda_snapshots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `study_agenda_snapshots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `new_cards` int NOT NULL,
  `review_cards` int NOT NULL,
  `listening_cards` int NOT NULL,
  `estimated_minutes` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `study_focus_areas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `study_focus_areas` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_id` bigint NOT NULL,
  `focus_label` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_focus_snapshot` (`snapshot_id`),
  CONSTRAINT `fk_focus_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `study_agenda_snapshots` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_word_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_word_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `vocabulary_entry_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `correct_count` int NOT NULL DEFAULT '0',
  `wrong_count` int NOT NULL DEFAULT '0',
  `last_answered_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_word_progress` (`user_id`,`vocabulary_entry_id`),
  KEY `fk_user_word_progress_entry` (`vocabulary_entry_id`),
  KEY `idx_user_word_progress_status` (`user_id`,`status`),
  CONSTRAINT `fk_user_word_progress_entry` FOREIGN KEY (`vocabulary_entry_id`) REFERENCES `vocabulary_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_word_progress_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `vocabulary_entries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vocabulary_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `wordbook_id` bigint NOT NULL,
  `word` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phonetic` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `example_sentence` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `definition_en` text COLLATE utf8mb4_unicode_ci,
  `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bnc_rank` int DEFAULT NULL,
  `frq_rank` int DEFAULT NULL,
  `wordfreq_zipf` decimal(4,2) DEFAULT NULL,
  `exchange_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `data_quality` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy',
  `difficulty` int NOT NULL,
  `visibility` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `audio_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `import_source` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'unknown',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_wordbook_word` (`user_id`,`wordbook_id`,`word`),
  KEY `fk_vocabulary_entries_wordbook` (`wordbook_id`),
  KEY `idx_vocabulary_user_wordbook` (`user_id`,`wordbook_id`),
  KEY `idx_vocabulary_visibility` (`visibility`),
  KEY `idx_vocabulary_word` (`word`),
  CONSTRAINT `fk_vocabulary_entries_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_vocabulary_entries_wordbook` FOREIGN KEY (`wordbook_id`) REFERENCES `wordbooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wordbooks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wordbooks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `platform` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `import_source` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'unknown',
  `word_count` int NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_wordbooks_user_created` (`user_id`,`created_at` DESC),
  CONSTRAINT `fk_wordbooks_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

