SET @schema_name = DATABASE();

SET @add_quiz_option_strategy = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `quiz_option_strategy` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''RANDOM'' AFTER `avatar_url`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'users'
    AND column_name = 'quiz_option_strategy'
);

PREPARE stmt FROM @add_quiz_option_strategy;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
