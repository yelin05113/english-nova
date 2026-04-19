package com.nightfall.englishnova.quiz.service.impl;

import com.nightfall.englishnova.quiz.mapper.QuizAttemptMapper;
import com.nightfall.englishnova.quiz.mapper.QuizSessionMapper;
import com.nightfall.englishnova.quiz.mapper.QuizUserWordProgressMapper;
import com.nightfall.englishnova.quiz.mapper.QuizVocabularyEntryMapper;
import com.nightfall.englishnova.quiz.mapper.QuizWordbookMapper;
import com.nightfall.englishnova.quiz.domain.po.AttemptPo;
import com.nightfall.englishnova.quiz.domain.vo.AttemptVo;
import com.nightfall.englishnova.quiz.domain.vo.EntryVo;
import com.nightfall.englishnova.quiz.domain.vo.QuestionVo;
import com.nightfall.englishnova.quiz.domain.vo.SessionVo;
import com.nightfall.englishnova.quiz.domain.vo.VocabularyEntryVo;
import com.nightfall.englishnova.quiz.domain.vo.WordbookProgressVo;
import com.nightfall.englishnova.quiz.domain.vo.WordbookSummaryVo;
import com.nightfall.englishnova.quiz.service.QuizService;
import com.nightfall.englishnova.quiz.utools.QuizTextUtools;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.CreateQuizSessionRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerResultDto;
import com.nightfall.englishnova.shared.dto.QuizQuestionDto;
import com.nightfall.englishnova.shared.dto.QuizSessionDto;
import com.nightfall.englishnova.shared.dto.QuizSessionStateDto;
import com.nightfall.englishnova.shared.dto.VocabularyEntryDto;
import com.nightfall.englishnova.shared.dto.WordbookProgressDto;
import com.nightfall.englishnova.shared.dto.WordbookSummaryDto;
import com.nightfall.englishnova.shared.enums.ProgressStatus;
import com.nightfall.englishnova.shared.enums.PromptType;
import com.nightfall.englishnova.shared.enums.QuizMode;
import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import com.nightfall.englishnova.shared.exception.ForbiddenException;
import com.nightfall.englishnova.shared.exception.NotFoundException;
import com.nightfall.englishnova.shared.text.TextRepairUtils;
import com.nightfall.englishnova.shared.text.UserFacingTextNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class QuizServiceImpl implements QuizService {

    private final QuizWordbookMapper wordbookMapper;
    private final QuizVocabularyEntryMapper vocabularyEntryMapper;
    private final QuizSessionMapper sessionMapper;
    private final QuizAttemptMapper attemptMapper;
    private final QuizUserWordProgressMapper progressMapper;

    public QuizServiceImpl(
            QuizWordbookMapper wordbookMapper,
            QuizVocabularyEntryMapper vocabularyEntryMapper,
            QuizSessionMapper sessionMapper,
            QuizAttemptMapper attemptMapper,
            QuizUserWordProgressMapper progressMapper
    ) {
        this.wordbookMapper = wordbookMapper;
        this.vocabularyEntryMapper = vocabularyEntryMapper;
        this.sessionMapper = sessionMapper;
        this.attemptMapper = attemptMapper;
        this.progressMapper = progressMapper;
    }

    @Override
    public List<WordbookSummaryDto> listWordbooks(CurrentUser user) {
        return wordbookMapper.listWordbooks(user.id()).stream()
                .map(this::mapWordbookSummary)
                .toList();
    }

    @Override
    public List<VocabularyEntryDto> listEntries(CurrentUser user, long wordbookId) {
        requireWordbook(user.id(), wordbookId);
        return vocabularyEntryMapper.listEntries(user.id(), wordbookId).stream()
                .map(this::mapVocabularyEntry)
                .toList();
    }

    @Override
    public WordbookProgressDto getWordbookProgress(CurrentUser user, long wordbookId) {
        requireWordbook(user.id(), wordbookId);
        WordbookProgressVo row = wordbookMapper.loadProgress(user.id(), wordbookId);
        if (row == null) {
            return new WordbookProgressDto(wordbookId, 0, 0, 0, 0);
        }
        return new WordbookProgressDto(
                wordbookId,
                row.getWordCount(),
                row.getClearedCount(),
                row.getInProgressCount(),
                row.getPendingCount()
        );
    }

    @Override
    @Transactional
    public QuizSessionStateDto createSession(CurrentUser user, CreateQuizSessionRequest request) {
        long wordbookId = request.wordbookId();
        QuizMode mode = request.mode() == null ? QuizMode.MIXED : request.mode();
        requireWordbook(user.id(), wordbookId);

        List<EntryVo> entries = vocabularyEntryMapper.loadWordbookEntries(user.id(), wordbookId);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("当前词书没有可斩杀的单词");
        }

        String sessionId = UUID.randomUUID().toString();
        sessionMapper.insertSession(
                sessionId,
                user.id(),
                wordbookId,
                mode.name(),
                entries.size(),
                Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant())
        );

        int index = 0;
        for (EntryVo entry : entries) {
            PromptType promptType = resolvePromptType(mode, index);
            AttemptPayload payload = buildAttemptPayload(user.id(), entry, promptType);
            attemptMapper.insertAttempt(toAttemptInsertRow(sessionId, user.id(), entry, promptType, payload));
            index++;
        }

        return getSessionState(user, sessionId);
    }

    @Override
    public QuizSessionStateDto getSessionState(CurrentUser user, String sessionId) {
        SessionVo session = requireSession(user.id(), sessionId);
        QuizQuestionDto nextQuestion = loadNextQuestion(sessionId, session.getTotalQuestions());
        if (nextQuestion == null && !"COMPLETED".equals(session.getStatus())) {
            sessionMapper.completeSession(sessionId);
            session = requireSession(user.id(), sessionId);
        }
        return new QuizSessionStateDto(mapSession(session), nextQuestion);
    }

    @Override
    @Transactional
    public QuizAnswerResultDto answer(CurrentUser user, String sessionId, QuizAnswerRequest request) {
        SessionVo session = requireSession(user.id(), sessionId);
        AttemptVo attempt = requireAttempt(user.id(), sessionId, request.attemptId());
        if (attempt.getSelectedOption() != null) {
            throw new IllegalArgumentException("该题已经作答");
        }

        boolean correct = attempt.getCorrectOption().equalsIgnoreCase(request.selectedOption().trim());
        progressMapper.updateAfterAnswer(
                user.id(),
                attempt.getVocabularyEntryId(),
                correct ? 1 : 0,
                correct ? 0 : 1,
                correct ? ProgressStatus.CLEARED.name() : ProgressStatus.IN_PROGRESS.name()
        );

        if (correct) {
            attemptMapper.markSelected(attempt.getId(), request.selectedOption().trim(), true);
            sessionMapper.markCorrectAnswer(sessionId);
        }

        SessionVo refreshedSession = requireSession(user.id(), sessionId);
        QuizQuestionDto nextQuestion = loadNextQuestion(sessionId, refreshedSession.getTotalQuestions());
        int remaining = Math.max(0, refreshedSession.getTotalQuestions() - refreshedSession.getAnsweredQuestions());
        return new QuizAnswerResultDto(
                correct,
                attempt.getCorrectOption(),
                remaining,
                mapSession(refreshedSession),
                nextQuestion
        );
    }

    private WordbookSummaryDto mapWordbookSummary(WordbookSummaryVo row) {
        return new WordbookSummaryDto(
                row.getId(),
                row.getName(),
                WordImportPlatform.valueOf(row.getPlatform()),
                row.getWordCount(),
                row.getClearedCount(),
                row.getPendingCount(),
                row.getCreatedAt().toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private VocabularyEntryDto mapVocabularyEntry(VocabularyEntryVo row) {
        return new VocabularyEntryDto(
                row.getId(),
                TextRepairUtils.repair(row.getWord()),
                QuizTextUtools.normalizePhonetic(row.getPhonetic()),
                UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getExampleSentence()),
                UserFacingTextNormalizer.normalizeMeaningText(row.getCategory()),
                row.getDifficulty(),
                row.getVisibility()
        );
    }

    private PromptType resolvePromptType(QuizMode mode, int index) {
        return switch (mode) {
            case CN_TO_EN -> PromptType.CN_TO_EN;
            case EN_TO_CN -> PromptType.EN_TO_CN;
            case MIXED -> index % 2 == 0 ? PromptType.CN_TO_EN : PromptType.EN_TO_CN;
        };
    }

    private AttemptPayload buildAttemptPayload(long userId, EntryVo entry, PromptType promptType) {
        String correctOption = promptType == PromptType.CN_TO_EN
                ? TextRepairUtils.repair(entry.getWord())
                : UserFacingTextNormalizer.normalizeMeaningText(entry.getMeaningCn());
        String promptText = promptType == PromptType.CN_TO_EN
                ? UserFacingTextNormalizer.normalizeMeaningText(entry.getMeaningCn())
                : TextRepairUtils.repair(entry.getWord());
        List<String> distractors = loadDistractors(userId, entry.getId(), promptType, correctOption);
        List<String> options = new ArrayList<>(distractors);
        options.add(correctOption);
        Collections.shuffle(options);
        return new AttemptPayload(promptText, options, correctOption);
    }

    private AttemptPo toAttemptInsertRow(String sessionId, long userId, EntryVo entry, PromptType promptType, AttemptPayload payload) {
        AttemptPo row = new AttemptPo();
        row.setSessionId(sessionId);
        row.setUserId(userId);
        row.setVocabularyEntryId(entry.getId());
        row.setPromptType(promptType.name());
        row.setPromptText(payload.promptText());
        row.setOptionA(payload.options().get(0));
        row.setOptionB(payload.options().get(1));
        row.setOptionC(payload.options().get(2));
        row.setOptionD(payload.options().get(3));
        row.setCorrectOption(payload.correctOption());
        row.setCreatedAt(Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));
        return row;
    }

    private List<String> loadDistractors(long userId, long entryId, PromptType promptType, String correctOption) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        List<String> userCandidates = promptType == PromptType.CN_TO_EN
                ? vocabularyEntryMapper.loadUserWordDistractors(userId, entryId)
                : vocabularyEntryMapper.loadUserMeaningDistractors(userId, entryId);
        addCandidates(values, userCandidates, correctOption);

        if (values.size() < 3) {
            List<String> publicCandidates = promptType == PromptType.CN_TO_EN
                    ? vocabularyEntryMapper.loadPublicWordDistractors(entryId)
                    : vocabularyEntryMapper.loadPublicMeaningDistractors(entryId);
            addCandidates(values, publicCandidates, correctOption);
        }

        if (values.size() < 3) {
            throw new IllegalArgumentException("当前可用干扰项不足，无法生成四选一题目");
        }
        return new ArrayList<>(values).subList(0, 3);
    }

    private void addCandidates(LinkedHashSet<String> values, List<String> candidates, String correctOption) {
        for (String candidate : candidates) {
            if (values.size() >= 3) {
                return;
            }
            addCandidate(values, candidate, correctOption);
        }
    }

    private void addCandidate(LinkedHashSet<String> values, String candidate, String correctOption) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String normalized = QuizTextUtools.hasHanCharacter(correctOption)
                ? UserFacingTextNormalizer.normalizeMeaningText(candidate).trim()
                : TextRepairUtils.repair(candidate).trim();
        if (normalized.equalsIgnoreCase(correctOption.trim())) {
            return;
        }
        values.add(normalized);
    }

    private SessionVo requireSession(long userId, String sessionId) {
        SessionVo session = sessionMapper.findByUserAndId(userId, sessionId);
        if (session == null) {
            throw new NotFoundException("未找到斩词会话");
        }
        return session;
    }

    private AttemptVo requireAttempt(long userId, String sessionId, long attemptId) {
        AttemptVo attempt = attemptMapper.findByUserSessionAndId(userId, sessionId, attemptId);
        if (attempt == null) {
            throw new NotFoundException("未找到题目");
        }
        return attempt;
    }

    private QuizQuestionDto loadNextQuestion(String sessionId, int totalQuestions) {
        QuestionVo row = attemptMapper.loadNextQuestion(sessionId);
        if (row == null) {
            return null;
        }
        return new QuizQuestionDto(
                row.getId(),
                PromptType.valueOf(row.getPromptType()),
                row.getPromptText(),
                List.of(row.getOptionA(), row.getOptionB(), row.getOptionC(), row.getOptionD()),
                sessionMapper.loadAnsweredCount(sessionId) + 1,
                totalQuestions
        );
    }

    private void requireWordbook(long userId, long wordbookId) {
        if (wordbookMapper.countOwnedWordbook(userId, wordbookId) == 0) {
            throw new ForbiddenException("你不能访问这个词书");
        }
    }

    private QuizSessionDto mapSession(SessionVo row) {
        return new QuizSessionDto(
                row.getId(),
                row.getWordbookId(),
                QuizMode.valueOf(row.getMode()),
                row.getTotalQuestions(),
                row.getAnsweredQuestions(),
                row.getCorrectAnswers(),
                row.getStatus()
        );
    }

    private record AttemptPayload(
            String promptText,
            List<String> options,
            String correctOption
    ) {
    }
}
