START TRANSACTION;

CREATE TEMPORARY TABLE tmp_target_public_wordbooks (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    word_count INT NOT NULL
);

INSERT INTO tmp_target_public_wordbooks (id, name, word_count)
SELECT id, name, word_count
FROM public_wordbooks
WHERE word_count > 0;

UPDATE quiz_sessions qs
JOIN tmp_target_public_wordbooks t ON t.id = qs.target_id
SET qs.status = 'CANCELLED',
    qs.finished_at = CURRENT_TIMESTAMP
WHERE qs.target_type = 'PUBLIC_WORDBOOK'
  AND qs.status = 'ACTIVE';

DELETE wrong_entries
FROM user_public_wordbook_wrong_entries wrong_entries
JOIN tmp_target_public_wordbooks t ON t.id = wrong_entries.public_wordbook_id;

UPDATE user_public_wordbooks subscriptions
JOIN tmp_target_public_wordbooks t ON t.id = subscriptions.public_wordbook_id
SET subscriptions.current_sort_order = 0,
    subscriptions.completed_count = 0,
    subscriptions.wrong_count = 0,
    subscriptions.daily_completed_count = 0,
    subscriptions.daily_progress_date = NULL,
    subscriptions.last_studied_at = NULL;

CREATE TEMPORARY TABLE tmp_public_wordbook_shuffle (
    public_wordbook_id BIGINT NOT NULL,
    public_entry_id BIGINT NOT NULL,
    new_sort_order INT NOT NULL,
    PRIMARY KEY (public_wordbook_id, public_entry_id),
    UNIQUE KEY uk_tmp_public_wordbook_shuffle_order (public_wordbook_id, new_sort_order)
);

INSERT INTO tmp_public_wordbook_shuffle (public_wordbook_id, public_entry_id, new_sort_order)
SELECT
    shuffled.public_wordbook_id,
    shuffled.public_entry_id,
    ROW_NUMBER() OVER (
        PARTITION BY shuffled.public_wordbook_id
        ORDER BY shuffled.shuffle_key, shuffled.public_entry_id
    ) AS new_sort_order
FROM (
    SELECT
        entries.public_wordbook_id,
        entries.public_entry_id,
        RAND() AS shuffle_key
    FROM public_wordbook_entries entries
    JOIN tmp_target_public_wordbooks t ON t.id = entries.public_wordbook_id
) shuffled;

UPDATE public_wordbook_entries entries
JOIN tmp_target_public_wordbooks t ON t.id = entries.public_wordbook_id
SET entries.sort_order = entries.sort_order + 1000000;

UPDATE public_wordbook_entries entries
JOIN tmp_public_wordbook_shuffle shuffled
  ON shuffled.public_wordbook_id = entries.public_wordbook_id
 AND shuffled.public_entry_id = entries.public_entry_id
SET entries.sort_order = shuffled.new_sort_order;

DROP TEMPORARY TABLE tmp_public_wordbook_shuffle;
DROP TEMPORARY TABLE tmp_target_public_wordbooks;

COMMIT;
