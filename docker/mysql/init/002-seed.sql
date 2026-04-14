
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
INSERT INTO `users` (`id`, `username`, `email`, `password_hash`, `status`, `created_at`) VALUES (1103,'system_public_catalog','system-public-catalog@englishnova.local','SYSTEM_EXTERNAL_IMPORT','ACTIVE','2026-04-12 14:06:22');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `vocabulary_entries` WRITE;
/*!40000 ALTER TABLE `vocabulary_entries` DISABLE KEYS */;
INSERT INTO `vocabulary_entries` (`id`, `user_id`, `wordbook_id`, `word`, `phonetic`, `meaning_cn`, `example_sentence`, `category`, `difficulty`, `visibility`, `audio_url`, `import_source`, `created_at`) VALUES (1,1103,1,'impact','/ɪmpækt/','影响，冲击','The policy had a visible impact on daily study habits.','公共核心',3,'PUBLIC','','public-core','2026-04-12 14:06:22'),(2,1103,1,'define','/dɪˈfaɪn/','解释，定义','Teachers define the target pattern before students repeat it.','公共核心',2,'PUBLIC','','public-core','2026-04-12 14:06:22'),(3,1103,1,'career','/kəˈrɪr/','生涯，职业','She treats vocabulary study as part of her future career plan.','公共核心',2,'PUBLIC','','public-core','2026-04-12 14:06:22'),(4,1103,1,'anchor','/ˈæŋkər/','锚点，支点','A short sentence can become the anchor for a new word family.','公共核心',3,'PUBLIC','','public-core','2026-04-12 14:06:22'),(5,1103,1,'retain','/rɪˈteɪn/','记住，保留','Spacing review helps learners retain fragile words.','公共核心',3,'PUBLIC','','public-core','2026-04-12 14:06:22'),(6,1103,1,'canopy','/ˈkænəpi/','树冠，顶篷','The forest canopy softened the noon light.','公共核心',4,'PUBLIC','','public-core','2026-04-12 14:06:22'),(7,1103,1,'meadow','/ˈmedoʊ/','草地，牧场','The path opened into a quiet meadow after the rain.','公共核心',3,'PUBLIC','','public-core','2026-04-12 14:06:22'),(8,1103,1,'brook','/brʊk/','小溪','A narrow brook cut across the old trail.','公共核心',3,'PUBLIC','','public-core','2026-04-12 14:06:22'),(9,1103,1,'sprout','/spraʊt/','发芽，萌发','New habits sprout when review becomes routine.','公共核心',3,'PUBLIC','','public-core','2026-04-12 14:06:22'),(10,1103,1,'clarify','/ˈklærəfaɪ/','澄清，阐明','Examples clarify the difference between close meanings.','公共核心',2,'PUBLIC','','public-core','2026-04-12 14:06:22'),(11,1103,1,'steady','/ˈstedi/','稳定的','A steady pace often beats short bursts of effort.','公共核心',2,'PUBLIC','','public-core','2026-04-12 14:06:22'),(12,1103,1,'merge','/mɜːrdʒ/','合并，融合','The app can merge public search and personal results.','公共核心',3,'PUBLIC','','public-core','2026-04-12 14:06:22');
/*!40000 ALTER TABLE `vocabulary_entries` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `wordbooks` WRITE;
/*!40000 ALTER TABLE `wordbooks` DISABLE KEYS */;
INSERT INTO `wordbooks` (`id`, `user_id`, `name`, `platform`, `source_name`, `import_source`, `word_count`, `created_at`) VALUES (1,1103,'公共词库','ANKI','public-core','public-core',12,'2026-04-12 14:06:22');
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

