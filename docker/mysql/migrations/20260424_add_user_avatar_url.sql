SET @schema_name = DATABASE();

SET @add_avatar_url = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `avatar_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `email`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'users'
    AND column_name = 'avatar_url'
);

PREPARE stmt FROM @add_avatar_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
