SET @schema_name = DATABASE();

SET @drop_quiz_attempts_user_entry_fk = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE `quiz_attempts` DROP FOREIGN KEY `fk_quiz_attempts_user_entry`',
    'SELECT 1'
  )
  FROM information_schema.table_constraints
  WHERE table_schema = @schema_name
    AND table_name = 'quiz_attempts'
    AND constraint_name = 'fk_quiz_attempts_user_entry'
    AND constraint_type = 'FOREIGN KEY'
);

PREPARE stmt FROM @drop_quiz_attempts_user_entry_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_word_notebook_entry_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `quiz_attempts`
      ADD COLUMN `word_notebook_entry_id` bigint DEFAULT NULL AFTER `user_vocabulary_entry_id`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'quiz_attempts'
    AND column_name = 'word_notebook_entry_id'
);

PREPARE stmt FROM @add_word_notebook_entry_column;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_word_notebook_entry_index = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `quiz_attempts`
      ADD KEY `fk_quiz_attempts_word_notebook_entry` (`word_notebook_entry_id`)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'quiz_attempts'
    AND index_name = 'fk_quiz_attempts_word_notebook_entry'
);

PREPARE stmt FROM @add_word_notebook_entry_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_quiz_attempts_user_entry_fk = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `quiz_attempts`
      ADD CONSTRAINT `fk_quiz_attempts_user_entry`
      FOREIGN KEY (`user_vocabulary_entry_id`) REFERENCES `user_vocabulary_entries` (`id`) ON DELETE CASCADE',
    'SELECT 1'
  )
  FROM information_schema.table_constraints
  WHERE table_schema = @schema_name
    AND table_name = 'quiz_attempts'
    AND constraint_name = 'fk_quiz_attempts_user_entry'
    AND constraint_type = 'FOREIGN KEY'
);

PREPARE stmt FROM @add_quiz_attempts_user_entry_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_quiz_attempts_word_notebook_entry_fk = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `quiz_attempts`
      ADD CONSTRAINT `fk_quiz_attempts_word_notebook_entry`
      FOREIGN KEY (`word_notebook_entry_id`) REFERENCES `word_notebook_entries` (`id`) ON DELETE CASCADE',
    'SELECT 1'
  )
  FROM information_schema.table_constraints
  WHERE table_schema = @schema_name
    AND table_name = 'quiz_attempts'
    AND constraint_name = 'fk_quiz_attempts_word_notebook_entry'
    AND constraint_type = 'FOREIGN KEY'
);

PREPARE stmt FROM @add_quiz_attempts_word_notebook_entry_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
