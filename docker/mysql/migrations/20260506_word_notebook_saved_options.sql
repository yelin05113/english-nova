SET @add_option_a = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_a` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `example_audio_url`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_a'
);
PREPARE stmt FROM @add_option_a;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_a_word = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_a_word` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_a`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_a_word'
);
PREPARE stmt FROM @add_option_a_word;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_a_meaning_cn = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_a_meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_a_word`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_a_meaning_cn'
);
PREPARE stmt FROM @add_option_a_meaning_cn;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_b = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_b` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_a_meaning_cn`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_b'
);
PREPARE stmt FROM @add_option_b;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_b_word = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_b_word` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_b`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_b_word'
);
PREPARE stmt FROM @add_option_b_word;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_b_meaning_cn = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_b_meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_b_word`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_b_meaning_cn'
);
PREPARE stmt FROM @add_option_b_meaning_cn;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_c = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_c` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_b_meaning_cn`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_c'
);
PREPARE stmt FROM @add_option_c;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_c_word = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_c_word` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_c`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_c_word'
);
PREPARE stmt FROM @add_option_c_word;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_c_meaning_cn = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_c_meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_c_word`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_c_meaning_cn'
);
PREPARE stmt FROM @add_option_c_meaning_cn;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_d = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_d` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_c_meaning_cn`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_d'
);
PREPARE stmt FROM @add_option_d;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_d_word = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_d_word` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_d`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_d_word'
);
PREPARE stmt FROM @add_option_d_word;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_option_d_meaning_cn = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `option_d_meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_d_word`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'option_d_meaning_cn'
);
PREPARE stmt FROM @add_option_d_meaning_cn;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_correct_option = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `word_notebook_entries` ADD COLUMN `correct_option` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' AFTER `option_d_meaning_cn`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'word_notebook_entries'
    AND column_name = 'correct_option'
);
PREPARE stmt FROM @add_correct_option;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
