
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

LOCK TABLES `import_tasks` WRITE;
/*!40000 ALTER TABLE `import_tasks` DISABLE KEYS */;
/*!40000 ALTER TABLE `import_tasks` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `quiz_attempts` WRITE;
/*!40000 ALTER TABLE `quiz_attempts` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_attempts` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `quiz_sessions` WRITE;
/*!40000 ALTER TABLE `quiz_sessions` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_sessions` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `study_agenda_snapshots` WRITE;
/*!40000 ALTER TABLE `study_agenda_snapshots` DISABLE KEYS */;
INSERT INTO `study_agenda_snapshots` (`id`, `new_cards`, `review_cards`, `listening_cards`, `estimated_minutes`) VALUES (1,24,86,18,32);
/*!40000 ALTER TABLE `study_agenda_snapshots` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `study_focus_areas` WRITE;
/*!40000 ALTER TABLE `study_focus_areas` DISABLE KEYS */;
INSERT INTO `study_focus_areas` (`id`, `snapshot_id`, `focus_label`, `sort_order`) VALUES (1,1,'Root families and affixes',1),(2,1,'Exam phrase clusters',2),(3,1,'Listening dictation review',3);
/*!40000 ALTER TABLE `study_focus_areas` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `user_word_progress` WRITE;
/*!40000 ALTER TABLE `user_word_progress` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_word_progress` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `vocabulary_entries` WRITE;
/*!40000 ALTER TABLE `vocabulary_entries` DISABLE KEYS */;
INSERT INTO `vocabulary_entries` (`id`, `user_id`, `wordbook_id`, `word`, `phonetic`, `meaning_cn`, `example_sentence`, `category`, `difficulty`, `visibility`, `created_at`) VALUES (1,NULL,1,'impact','/ÉªmpÃ¦kt/','å½±å“ï¼Œå†²å‡»','The policy had a visible impact on daily study habits.','å…¬å…±æ ¸å¿ƒ',3,'PUBLIC','2026-04-12 14:06:22'),(2,NULL,1,'define','/dÉªËˆfaÉªn/','è§£é‡Šï¼Œå®šä¹‰','Teachers define the target pattern before students repeat it.','å…¬å…±æ ¸å¿ƒ',2,'PUBLIC','2026-04-12 14:06:22'),(3,NULL,1,'career','/kÉ™ËˆrÉªr/','ç”Ÿæ¶¯ï¼ŒèŒä¸š','She treats vocabulary study as part of her future career plan.','å…¬å…±æ ¸å¿ƒ',2,'PUBLIC','2026-04-12 14:06:22'),(4,NULL,1,'anchor','/ËˆÃ¦Å‹kÉ™r/','é”šç‚¹ï¼Œæ”¯ç‚¹','A short sentence can become the anchor for a new word family.','å…¬å…±æ ¸å¿ƒ',3,'PUBLIC','2026-04-12 14:06:22'),(5,NULL,1,'retain','/rÉªËˆteÉªn/','è®°ä½ï¼Œä¿ç•™','Spacing review helps learners retain fragile words.','å…¬å…±æ ¸å¿ƒ',3,'PUBLIC','2026-04-12 14:06:22'),(6,NULL,1,'canopy','/ËˆkÃ¦nÉ™pi/','æ ‘å† ï¼Œé¡¶ç¯·','The forest canopy softened the noon light.','å…¬å…±æ ¸å¿ƒ',4,'PUBLIC','2026-04-12 14:06:22'),(7,NULL,1,'meadow','/ËˆmedoÊŠ/','è‰åœ°ï¼Œç‰§åœº','The path opened into a quiet meadow after the rain.','å…¬å…±æ ¸å¿ƒ',3,'PUBLIC','2026-04-12 14:06:22'),(8,NULL,1,'brook','/brÊŠk/','å°æºª','A narrow brook cut across the old trail.','å…¬å…±æ ¸å¿ƒ',3,'PUBLIC','2026-04-12 14:06:22'),(9,NULL,1,'sprout','/spraÊŠt/','å‘èŠ½ï¼ŒèŒå‘','New habits sprout when review becomes routine.','å…¬å…±æ ¸å¿ƒ',3,'PUBLIC','2026-04-12 14:06:22'),(10,NULL,1,'clarify','/ËˆklÃ¦rÉ™faÉª/','æ¾„æ¸…ï¼Œé˜æ˜Ž','Examples clarify the difference between close meanings.','å…¬å…±æ ¸å¿ƒ',2,'PUBLIC','2026-04-12 14:06:22'),(11,NULL,1,'steady','/Ëˆstedi/','ç¨³å®šçš„','A steady pace often beats short bursts of effort.','å…¬å…±æ ¸å¿ƒ',2,'PUBLIC','2026-04-12 14:06:22'),(12,NULL,1,'merge','/mÉœËrdÊ’/','åˆå¹¶ï¼Œèžåˆ','The app can merge public search and personal results.','å…¬å…±æ ¸å¿ƒ',3,'PUBLIC','2026-04-12 14:06:22');
/*!40000 ALTER TABLE `vocabulary_entries` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `wordbooks` WRITE;
/*!40000 ALTER TABLE `wordbooks` DISABLE KEYS */;
INSERT INTO `wordbooks` (`id`, `user_id`, `name`, `platform`, `source_name`, `word_count`, `created_at`) VALUES (1,NULL,'å…¬å…±è¯åº“','ANKI','public-core',12,'2026-04-12 14:06:22');
/*!40000 ALTER TABLE `wordbooks` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

