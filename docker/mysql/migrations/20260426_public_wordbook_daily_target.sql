ALTER TABLE `user_public_wordbooks`
  ADD COLUMN IF NOT EXISTS `daily_target_count` int NOT NULL DEFAULT '0' AFTER `wrong_count`,
  ADD COLUMN IF NOT EXISTS `daily_completed_count` int NOT NULL DEFAULT '0' AFTER `daily_target_count`,
  ADD COLUMN IF NOT EXISTS `daily_progress_date` date DEFAULT NULL AFTER `daily_completed_count`;
