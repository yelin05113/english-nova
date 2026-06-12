SET @schema_name = DATABASE();

SET @drop_public_wordbooks_tag_index = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `public_wordbooks` DROP INDEX `uk_public_wordbooks_tag`'
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'public_wordbooks'
    AND index_name = 'uk_public_wordbooks_tag'
);

PREPARE stmt FROM @drop_public_wordbooks_tag_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_public_wordbooks_tag_column = (
  SELECT IF(
    COUNT(*) = 0,
    'SELECT 1',
    'ALTER TABLE `public_wordbooks` DROP COLUMN `tag`'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'public_wordbooks'
    AND column_name = 'tag'
);

PREPARE stmt FROM @drop_public_wordbooks_tag_column;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_public_wordbooks_name_index = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `public_wordbooks` ADD UNIQUE KEY `uk_public_wordbooks_name` (`name`)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'public_wordbooks'
    AND index_name = 'uk_public_wordbooks_name'
);

PREPARE stmt FROM @add_public_wordbooks_name_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
