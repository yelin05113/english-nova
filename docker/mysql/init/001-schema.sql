
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
  CONSTRAINT `fk_public_catalog_import_items_entry` FOREIGN KEY (`entry_id`) REFERENCES `public_vocabulary_entries` (`id`) ON DELETE SET NULL,
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
  `user_vocabulary_entry_id` bigint DEFAULT NULL,
  `public_entry_id` bigint DEFAULT NULL,
  `prompt_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_text` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_a` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_b` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_c` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_d` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `correct_option` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `wrong_submissions` int NOT NULL DEFAULT '0',
  `selected_option` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_correct` tinyint(1) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `answered_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_quiz_attempts_user_entry` (`user_vocabulary_entry_id`),
  KEY `fk_quiz_attempts_public_entry` (`public_entry_id`),
  KEY `idx_quiz_attempts_session_answered` (`session_id`,`answered_at`),
  KEY `idx_quiz_attempts_user_created` (`user_id`,`created_at` DESC),
  CONSTRAINT `fk_quiz_attempts_public_entry` FOREIGN KEY (`public_entry_id`) REFERENCES `public_vocabulary_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_quiz_attempts_session` FOREIGN KEY (`session_id`) REFERENCES `quiz_sessions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_quiz_attempts_user_entry` FOREIGN KEY (`user_vocabulary_entry_id`) REFERENCES `user_vocabulary_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_quiz_attempts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `quiz_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_sessions` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `target_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_offset` int NOT NULL DEFAULT '0',
  `total_questions` int NOT NULL,
  `answered_questions` int NOT NULL DEFAULT '0',
  `correct_answers` int NOT NULL DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at` timestamp NOT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_quiz_sessions_target` (`user_id`,`target_type`,`target_id`,`status`),
  KEY `idx_quiz_sessions_user_started` (`user_id`,`started_at` DESC),
  CONSTRAINT `fk_quiz_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
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
  CONSTRAINT `fk_user_word_progress_entry` FOREIGN KEY (`vocabulary_entry_id`) REFERENCES `user_vocabulary_entries` (`id`) ON DELETE CASCADE,
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
  `avatar_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password_hash` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `public_vocabulary_entries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_vocabulary_entries` (
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `public_wordbooks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_wordbooks` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `public_wordbook_entries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_wordbook_entries` (
  `public_wordbook_id` bigint NOT NULL,
  `public_entry_id` bigint NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`public_wordbook_id`,`public_entry_id`),
  UNIQUE KEY `uk_public_wordbook_entries_order` (`public_wordbook_id`,`sort_order`),
  KEY `idx_public_wordbook_entries_entry` (`public_entry_id`),
  CONSTRAINT `fk_public_wordbook_entries_entry` FOREIGN KEY (`public_entry_id`) REFERENCES `public_vocabulary_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_public_wordbook_entries_wordbook` FOREIGN KEY (`public_wordbook_id`) REFERENCES `public_wordbooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_public_wordbook_wrong_entries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_public_wordbook_wrong_entries` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_public_wordbooks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_public_wordbooks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `public_wordbook_id` bigint NOT NULL,
  `current_sort_order` int NOT NULL DEFAULT '0',
  `completed_count` int NOT NULL DEFAULT '0',
  `wrong_count` int NOT NULL DEFAULT '0',
  `daily_target_count` int NOT NULL DEFAULT '0',
  `daily_completed_count` int NOT NULL DEFAULT '0',
  `daily_progress_date` date DEFAULT NULL,
  `subscribed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_studied_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_public_wordbook` (`user_id`,`public_wordbook_id`),
  KEY `idx_user_public_wordbooks_user` (`user_id`,`last_studied_at` DESC),
  CONSTRAINT `fk_user_public_wordbooks_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_public_wordbooks_wordbook` FOREIGN KEY (`public_wordbook_id`) REFERENCES `public_wordbooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_vocabulary_entries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_vocabulary_entries` (
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

