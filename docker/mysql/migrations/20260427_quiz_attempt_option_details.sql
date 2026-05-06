SET @schema_name = DATABASE();

SET @add_quiz_attempt_option_details = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `quiz_attempts`
      ADD COLUMN `option_a_word` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `option_a`,
      ADD COLUMN `option_a_meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `option_a_word`,
      ADD COLUMN `option_b_word` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `option_b`,
      ADD COLUMN `option_b_meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `option_b_word`,
      ADD COLUMN `option_c_word` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `option_c`,
      ADD COLUMN `option_c_meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `option_c_word`,
      ADD COLUMN `option_d_word` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `option_d`,
      ADD COLUMN `option_d_meaning_cn` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `option_d_word`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'quiz_attempts'
    AND column_name = 'option_a_word'
);

PREPARE stmt FROM @add_quiz_attempt_option_details;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
