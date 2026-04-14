package com.heima.englishnova.quiz.service;

import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.dto.CreateQuizSessionRequest;
import com.heima.englishnova.shared.dto.QuizAnswerRequest;
import com.heima.englishnova.shared.dto.QuizAnswerResultDto;
import com.heima.englishnova.shared.dto.QuizQuestionDto;
import com.heima.englishnova.shared.dto.QuizSessionDto;
import com.heima.englishnova.shared.dto.QuizSessionStateDto;
import com.heima.englishnova.shared.dto.VocabularyEntryDto;
import com.heima.englishnova.shared.dto.WordbookProgressDto;
import com.heima.englishnova.shared.dto.WordbookSummaryDto;
import com.heima.englishnova.shared.enums.ProgressStatus;
import com.heima.englishnova.shared.enums.PromptType;
import com.heima.englishnova.shared.enums.QuizMode;
import com.heima.englishnova.shared.enums.WordImportPlatform;
import com.heima.englishnova.shared.exception.ForbiddenException;
import com.heima.englishnova.shared.exception.NotFoundException;
import com.heima.englishnova.shared.text.TextRepairUtils;
import com.heima.englishnova.shared.text.UserFacingTextNormalizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class QuizService {

    private final JdbcTemplate jdbcTemplate;

    public QuizService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WordbookSummaryDto> listWordbooks(CurrentUser user) {
        return jdbcTemplate.query(
                """
                SELECT
                    w.id,
                    w.name,
                    w.platform,
                    w.word_count,
                    w.created_at,
                    COALESCE(SUM(CASE WHEN p.status = 'CLEARED' THEN 1 ELSE 0 END), 0) AS cleared_count,
                    COALESCE(SUM(CASE WHEN p.status <> 'CLEARED' THEN 1 ELSE 0 END), 0) AS pending_count
                FROM wordbooks w
                LEFT JOIN vocabulary_entries v ON v.wordbook_id = w.id
                LEFT JOIN user_word_progress p ON p.vocabulary_entry_id = v.id AND p.user_id = w.user_id
                WHERE w.user_id = ?
                GROUP BY w.id, w.name, w.platform, w.word_count, w.created_at
                ORDER BY w.created_at DESC
                """,
                (resultSet, rowNum) -> new WordbookSummaryDto(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        WordImportPlatform.valueOf(resultSet.getString("platform")),
                        resultSet.getInt("word_count"),
                        resultSet.getInt("cleared_count"),
                        resultSet.getInt("pending_count"),
                        resultSet.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC)
                ),
                user.id()
        );
    }

    public List<VocabularyEntryDto> listEntries(CurrentUser user, long wordbookId) {
        requireWordbook(user.id(), wordbookId);
        return jdbcTemplate.query(
                """
                SELECT id, word, phonetic, meaning_cn, example_sentence, category, difficulty, visibility
                FROM vocabulary_entries
                WHERE user_id = ? AND wordbook_id = ?
                ORDER BY word ASC
                """,
                (resultSet, rowNum) -> new VocabularyEntryDto(
                        resultSet.getLong("id"),
                        TextRepairUtils.repair(resultSet.getString("word")),
                        normalizePhonetic(resultSet.getString("phonetic")),
                        UserFacingTextNormalizer.normalizeMeaningText(resultSet.getString("meaning_cn")),
                        UserFacingTextNormalizer.normalizeDisplayText(resultSet.getString("example_sentence")),
                        UserFacingTextNormalizer.normalizeMeaningText(resultSet.getString("category")),
                        resultSet.getInt("difficulty"),
                        resultSet.getString("visibility")
                ),
                user.id(),
                wordbookId
        );
    }

    public WordbookProgressDto getWordbookProgress(CurrentUser user, long wordbookId) {
        requireWordbook(user.id(), wordbookId);
        return jdbcTemplate.query(
                """
                SELECT
                    COUNT(v.id) AS word_count,
                    COALESCE(SUM(CASE WHEN p.status = 'CLEARED' THEN 1 ELSE 0 END), 0) AS cleared_count,
                    COALESCE(SUM(CASE WHEN p.status = 'IN_PROGRESS' THEN 1 ELSE 0 END), 0) AS in_progress_count,
                    COALESCE(SUM(CASE WHEN p.status = 'NEW' THEN 1 ELSE 0 END), 0) AS pending_count
                FROM vocabulary_entries v
                LEFT JOIN user_word_progress p
                    ON p.vocabulary_entry_id = v.id AND p.user_id = ?
                WHERE v.user_id = ? AND v.wordbook_id = ?
                """,
                resultSet -> resultSet.next()
                        ? new WordbookProgressDto(
                        wordbookId,
                        resultSet.getInt("word_count"),
                        resultSet.getInt("cleared_count"),
                        resultSet.getInt("in_progress_count"),
                        resultSet.getInt("pending_count")
                )
                        : new WordbookProgressDto(wordbookId, 0, 0, 0, 0),
                user.id(),
                user.id(),
                wordbookId
        );
    }

    @Transactional
    public QuizSessionStateDto createSession(CurrentUser user, CreateQuizSessionRequest request) {
        long wordbookId = request.wordbookId();
        QuizMode mode = request.mode() == null ? QuizMode.MIXED : request.mode();
        requireWordbook(user.id(), wordbookId);

        List<EntryRow> entries = loadWordbookEntries(user.id(), wordbookId);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("当前词书没有可斩杀的单词");
        }

        String sessionId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO quiz_sessions(id, user_id, wordbook_id, mode, total_questions, answered_questions, correct_answers, status, started_at)
                VALUES (?, ?, ?, ?, ?, 0, 0, 'ACTIVE', ?)
                """,
                sessionId,
                user.id(),
                wordbookId,
                mode.name(),
                entries.size(),
                Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant())
        );

        int index = 0;
        for (EntryRow entry : entries) {
            PromptType promptType = resolvePromptType(mode, index);
            AttemptPayload payload = buildAttemptPayload(user.id(), entry, promptType);
            jdbcTemplate.update(
                    """
                    INSERT INTO quiz_attempts(
                        session_id, user_id, vocabulary_entry_id, prompt_type, prompt_text,
                        option_a, option_b, option_c, option_d, correct_option, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    sessionId,
                    user.id(),
                    entry.id(),
                    promptType.name(),
                    payload.promptText(),
                    payload.options().get(0),
                    payload.options().get(1),
                    payload.options().get(2),
                    payload.options().get(3),
                    payload.correctOption(),
                    Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant())
            );
            index++;
        }

        return getSessionState(user, sessionId);
    }

    public QuizSessionStateDto getSessionState(CurrentUser user, String sessionId) {
        SessionRow session = requireSession(user.id(), sessionId);
        QuizQuestionDto nextQuestion = loadNextQuestion(sessionId, session.totalQuestions());
        if (nextQuestion == null && !"COMPLETED".equals(session.status())) {
            jdbcTemplate.update(
                    "UPDATE quiz_sessions SET status = 'COMPLETED', finished_at = CURRENT_TIMESTAMP WHERE id = ?",
                    sessionId
            );
            session = requireSession(user.id(), sessionId);
        }
        return new QuizSessionStateDto(mapSession(session), nextQuestion);
    }

    @Transactional
    public QuizAnswerResultDto answer(CurrentUser user, String sessionId, QuizAnswerRequest request) {
        SessionRow session = requireSession(user.id(), sessionId);
        AttemptRow attempt = requireAttempt(user.id(), sessionId, request.attemptId());
        if (attempt.selectedOption() != null) {
            throw new IllegalArgumentException("该题已经作答");
        }

        boolean correct = attempt.correctOption().equalsIgnoreCase(request.selectedOption().trim());

        jdbcTemplate.update(
                """
                UPDATE user_word_progress
                SET correct_count = correct_count + ?,
                    wrong_count = wrong_count + ?,
                    status = ?,
                    last_answered_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND vocabulary_entry_id = ?
                """,
                correct ? 1 : 0,
                correct ? 0 : 1,
                correct ? ProgressStatus.CLEARED.name() : ProgressStatus.IN_PROGRESS.name(),
                user.id(),
                attempt.vocabularyEntryId()
        );

        if (correct) {
            jdbcTemplate.update(
                    """
                    UPDATE quiz_attempts
                    SET selected_option = ?, is_correct = ?, answered_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    request.selectedOption().trim(),
                    true,
                    attempt.id()
            );

            jdbcTemplate.update(
                    """
                    UPDATE quiz_sessions
                    SET answered_questions = answered_questions + 1,
                        correct_answers = correct_answers + 1,
                        status = CASE
                            WHEN answered_questions + 1 >= total_questions THEN 'COMPLETED'
                            ELSE status
                        END,
                        finished_at = CASE
                            WHEN answered_questions + 1 >= total_questions THEN CURRENT_TIMESTAMP
                            ELSE finished_at
                        END
                    WHERE id = ?
                    """,
                    sessionId
            );
        }

        SessionRow refreshedSession = requireSession(user.id(), sessionId);
        QuizQuestionDto nextQuestion = loadNextQuestion(sessionId, refreshedSession.totalQuestions());
        int remaining = Math.max(0, refreshedSession.totalQuestions() - refreshedSession.answeredQuestions());
        return new QuizAnswerResultDto(
                correct,
                attempt.correctOption(),
                remaining,
                mapSession(refreshedSession),
                nextQuestion
        );
    }

    private PromptType resolvePromptType(QuizMode mode, int index) {
        return switch (mode) {
            case CN_TO_EN -> PromptType.CN_TO_EN;
            case EN_TO_CN -> PromptType.EN_TO_CN;
            case MIXED -> index % 2 == 0 ? PromptType.CN_TO_EN : PromptType.EN_TO_CN;
        };
    }

    private AttemptPayload buildAttemptPayload(long userId, EntryRow entry, PromptType promptType) {
        String correctOption = promptType == PromptType.CN_TO_EN
                ? TextRepairUtils.repair(entry.word())
                : UserFacingTextNormalizer.normalizeMeaningText(entry.meaningCn());
        String promptText = promptType == PromptType.CN_TO_EN
                ? UserFacingTextNormalizer.normalizeMeaningText(entry.meaningCn())
                : TextRepairUtils.repair(entry.word());
        List<String> distractors = loadDistractors(userId, entry.id(), promptType, correctOption);
        List<String> options = new ArrayList<>(distractors);
        options.add(correctOption);
        Collections.shuffle(options);
        return new AttemptPayload(promptText, options, correctOption);
    }

    private List<String> loadDistractors(long userId, long entryId, PromptType promptType, String correctOption) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String field = promptType == PromptType.CN_TO_EN ? "word" : "meaning_cn";

        jdbcTemplate.query(
                "SELECT " + field + " AS candidate FROM vocabulary_entries WHERE user_id = ? AND id <> ? ORDER BY RAND() LIMIT 12",
                resultSet -> {
                    while (resultSet.next() && values.size() < 3) {
                        addCandidate(values, resultSet.getString("candidate"), correctOption);
                    }
                },
                userId,
                entryId
        );

        if (values.size() < 3) {
            jdbcTemplate.query(
                    "SELECT " + field + " AS candidate FROM vocabulary_entries WHERE visibility = 'PUBLIC' AND id <> ? ORDER BY RAND() LIMIT 12",
                    resultSet -> {
                        while (resultSet.next() && values.size() < 3) {
                            addCandidate(values, resultSet.getString("candidate"), correctOption);
                        }
                    },
                    entryId
            );
        }

        if (values.size() < 3) {
            throw new IllegalArgumentException("当前可用干扰项不足，无法生成四选一题目");
        }
        return new ArrayList<>(values);
    }

    private void addCandidate(LinkedHashSet<String> values, String candidate, String correctOption) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String normalized = promptLooksChinese(correctOption)
                ? UserFacingTextNormalizer.normalizeMeaningText(candidate).trim()
                : TextRepairUtils.repair(candidate).trim();
        if (normalized.equalsIgnoreCase(correctOption.trim())) {
            return;
        }
        values.add(normalized);
    }

    private boolean promptLooksChinese(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private SessionRow requireSession(long userId, String sessionId) {
        List<SessionRow> sessions = jdbcTemplate.query(
                """
                SELECT id, user_id, wordbook_id, mode, total_questions, answered_questions, correct_answers, status
                FROM quiz_sessions
                WHERE id = ? AND user_id = ?
                LIMIT 1
                """,
                (resultSet, rowNum) -> new SessionRow(
                        resultSet.getString("id"),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("wordbook_id"),
                        QuizMode.valueOf(resultSet.getString("mode")),
                        resultSet.getInt("total_questions"),
                        resultSet.getInt("answered_questions"),
                        resultSet.getInt("correct_answers"),
                        resultSet.getString("status")
                ),
                sessionId,
                userId
        );
        if (sessions.isEmpty()) {
            throw new NotFoundException("未找到斩词会话");
        }
        return sessions.get(0);
    }

    private AttemptRow requireAttempt(long userId, String sessionId, long attemptId) {
        List<AttemptRow> attempts = jdbcTemplate.query(
                """
                SELECT id, vocabulary_entry_id, correct_option, selected_option
                FROM quiz_attempts
                WHERE id = ? AND session_id = ? AND user_id = ?
                LIMIT 1
                """,
                (resultSet, rowNum) -> new AttemptRow(
                        resultSet.getLong("id"),
                        resultSet.getLong("vocabulary_entry_id"),
                        resultSet.getString("correct_option"),
                        resultSet.getString("selected_option")
                ),
                attemptId,
                sessionId,
                userId
        );
        if (attempts.isEmpty()) {
            throw new NotFoundException("未找到题目");
        }
        return attempts.get(0);
    }

    private QuizQuestionDto loadNextQuestion(String sessionId, int totalQuestions) {
        List<QuizQuestionDto> questions = jdbcTemplate.query(
                """
                SELECT id, prompt_type, prompt_text, option_a, option_b, option_c, option_d
                FROM quiz_attempts
                WHERE session_id = ? AND selected_option IS NULL
                ORDER BY id ASC
                LIMIT 1
                """,
                (resultSet, rowNum) -> new QuizQuestionDto(
                        resultSet.getLong("id"),
                        PromptType.valueOf(resultSet.getString("prompt_type")),
                        resultSet.getString("prompt_text"),
                        List.of(
                                resultSet.getString("option_a"),
                                resultSet.getString("option_b"),
                                resultSet.getString("option_c"),
                                resultSet.getString("option_d")
                        ),
                        loadAnsweredCount(sessionId) + 1,
                        totalQuestions
                ),
                sessionId
        );
        return questions.isEmpty() ? null : questions.get(0);
    }

    private int loadAnsweredCount(String sessionId) {
        Integer answered = jdbcTemplate.queryForObject(
                "SELECT answered_questions FROM quiz_sessions WHERE id = ?",
                Integer.class,
                sessionId
        );
        return answered == null ? 0 : answered;
    }

    private List<EntryRow> loadWordbookEntries(long userId, long wordbookId) {
        return jdbcTemplate.query(
                """
                SELECT id, word, meaning_cn
                FROM vocabulary_entries
                WHERE user_id = ? AND wordbook_id = ?
                ORDER BY id ASC
                """,
                (resultSet, rowNum) -> new EntryRow(
                        resultSet.getLong("id"),
                        TextRepairUtils.repair(resultSet.getString("word")),
                        UserFacingTextNormalizer.normalizeMeaningText(resultSet.getString("meaning_cn"))
                ),
                userId,
                wordbookId
        );
    }

    private void requireWordbook(long userId, long wordbookId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wordbooks WHERE id = ? AND user_id = ?",
                Integer.class,
                wordbookId,
                userId
        );
        if (count == null || count == 0) {
            throw new ForbiddenException("你不能访问这个词书");
        }
    }

    private QuizSessionDto mapSession(SessionRow row) {
        return new QuizSessionDto(
                row.id(),
                row.wordbookId(),
                row.mode(),
                row.totalQuestions(),
                row.answeredQuestions(),
                row.correctAnswers(),
                row.status()
        );
    }

    private String normalizePhonetic(String phonetic) {
        if (phonetic == null) {
            return "";
        }
        return phonetic.trim();
    }

    private record EntryRow(
            long id,
            String word,
            String meaningCn
    ) {
    }

    private record AttemptPayload(
            String promptText,
            List<String> options,
            String correctOption
    ) {
    }

    private record SessionRow(
            String id,
            long userId,
            long wordbookId,
            QuizMode mode,
            int totalQuestions,
            int answeredQuestions,
            int correctAnswers,
            String status
    ) {
    }

    private record AttemptRow(
            long id,
            long vocabularyEntryId,
            String correctOption,
            String selectedOption
    ) {
    }
}
