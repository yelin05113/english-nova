package com.heima.englishnova.study.service;

import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.auth.RequestUserExtractor;
import com.heima.englishnova.shared.dto.StudyAgendaDto;
import com.heima.englishnova.shared.dto.StudyProgressDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudyAgendaService {

    private final JdbcTemplate jdbcTemplate;

    public StudyAgendaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StudyAgendaDto getTodayAgenda(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        ProgressRow progress = loadProgress(user.id());
        int listeningCards = Math.min(12, Math.max(0, progress.totalWords() / 5));
        int estimatedMinutes = Math.max(5, (progress.newWords() * 2) + progress.inProgressWords());
        return new StudyAgendaDto(
                progress.newWords(),
                progress.inProgressWords(),
                listeningCards,
                estimatedMinutes,
                buildFocusAreas(progress)
        );
    }

    public StudyProgressDto getProgress(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        ProgressRow progress = loadProgress(user.id());
        AccuracyRow accuracy = jdbcTemplate.query(
                """
                SELECT COUNT(*) AS answered_questions,
                       COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0) AS correct_answers
                FROM quiz_attempts
                WHERE user_id = ?
                LIMIT 1
                """,
                resultSet -> resultSet.next()
                        ? new AccuracyRow(
                        resultSet.getInt("answered_questions"),
                        resultSet.getInt("correct_answers")
                )
                        : new AccuracyRow(0, 0),
                user.id()
        );
        int accuracyRate = accuracy.answeredQuestions() == 0
                ? 0
                : Math.round((accuracy.correctAnswers() * 100f) / accuracy.answeredQuestions());

        return new StudyProgressDto(
                progress.totalWords(),
                progress.clearedWords(),
                progress.inProgressWords(),
                progress.newWords(),
                progress.wordbooks(),
                accuracy.answeredQuestions(),
                accuracy.correctAnswers(),
                accuracyRate
        );
    }

    private ProgressRow loadProgress(long userId) {
        return jdbcTemplate.query(
                """
                SELECT
                    COUNT(v.id) AS total_words,
                    COALESCE(SUM(CASE WHEN p.status = 'CLEARED' THEN 1 ELSE 0 END), 0) AS cleared_words,
                    COALESCE(SUM(CASE WHEN p.status = 'IN_PROGRESS' THEN 1 ELSE 0 END), 0) AS in_progress_words,
                    COALESCE(SUM(CASE WHEN p.status = 'NEW' THEN 1 ELSE 0 END), 0) AS new_words,
                    COUNT(DISTINCT v.wordbook_id) AS wordbooks
                FROM vocabulary_entries v
                LEFT JOIN user_word_progress p
                    ON p.vocabulary_entry_id = v.id AND p.user_id = ?
                WHERE v.user_id = ?
                """,
                resultSet -> resultSet.next()
                        ? new ProgressRow(
                        resultSet.getInt("total_words"),
                        resultSet.getInt("cleared_words"),
                        resultSet.getInt("in_progress_words"),
                        resultSet.getInt("new_words"),
                        resultSet.getInt("wordbooks")
                )
                        : new ProgressRow(0, 0, 0, 0, 0),
                userId,
                userId
        );
    }

    private List<String> buildFocusAreas(ProgressRow progress) {
        if (progress.totalWords() == 0) {
            return List.of("先导入一个词书，系统会自动生成你的个人学习面板");
        }
        if (progress.inProgressWords() > progress.clearedWords()) {
            return List.of("优先清理答错词", "把未斩词书先开一轮", "搜索薄弱词义做强化");
        }
        if (progress.newWords() > 0) {
            return List.of("先处理新词", "保持单词斩杀节奏", "完成后再回看搜索高频词");
        }
        return List.of("本轮词书接近清空", "可以开始下一本词书", "继续巩固公共词库干扰项");
    }

    private record ProgressRow(
            int totalWords,
            int clearedWords,
            int inProgressWords,
            int newWords,
            int wordbooks
    ) {
    }

    private record AccuracyRow(
            int answeredQuestions,
            int correctAnswers
    ) {
    }
}
