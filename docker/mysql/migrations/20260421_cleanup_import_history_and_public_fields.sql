-- Remove import-history/study snapshot tables and the public-entry fields that are no longer used.

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `import_tasks`;
DROP TABLE IF EXISTS `study_focus_areas`;
DROP TABLE IF EXISTS `study_agenda_snapshots`;

SET @schema_name = DATABASE();

SET @drop_definition_en = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE `public_vocabulary_entries` DROP COLUMN `definition_en`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'public_vocabulary_entries'
    AND column_name = 'definition_en'
);
PREPARE stmt FROM @drop_definition_en;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_tags = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE `public_vocabulary_entries` DROP COLUMN `tags`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'public_vocabulary_entries'
    AND column_name = 'tags'
);
PREPARE stmt FROM @drop_tags;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
