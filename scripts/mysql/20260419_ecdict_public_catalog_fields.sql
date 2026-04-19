SET @schema_name = DATABASE();

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vocabulary_entries` ADD COLUMN `definition_en` text COLLATE utf8mb4_unicode_ci',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'vocabulary_entries' AND column_name = 'definition_en'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `wordbooks`
SET platform = 'ECDICT',
    source_name = 'ECDICT + dictionaryapi.dev',
    import_source = 'ecdict'
WHERE user_id = 1103
  AND name = 'English Nova Public Catalog';

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vocabulary_entries` ADD COLUMN `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'vocabulary_entries' AND column_name = 'tags'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vocabulary_entries` ADD COLUMN `bnc_rank` int DEFAULT NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'vocabulary_entries' AND column_name = 'bnc_rank'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vocabulary_entries` ADD COLUMN `frq_rank` int DEFAULT NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'vocabulary_entries' AND column_name = 'frq_rank'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vocabulary_entries` ADD COLUMN `wordfreq_zipf` decimal(4,2) DEFAULT NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'vocabulary_entries' AND column_name = 'wordfreq_zipf'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vocabulary_entries` ADD COLUMN `exchange_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'vocabulary_entries' AND column_name = 'exchange_info'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `vocabulary_entries` ADD COLUMN `data_quality` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''legacy''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'vocabulary_entries' AND column_name = 'data_quality'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `public_catalog_import_items` ADD COLUMN `has_definition` tinyint(1) NOT NULL DEFAULT ''0''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'public_catalog_import_items' AND column_name = 'has_definition'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `public_catalog_import_items` ADD COLUMN `has_frequency` tinyint(1) NOT NULL DEFAULT ''0''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'public_catalog_import_items' AND column_name = 'has_frequency'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
