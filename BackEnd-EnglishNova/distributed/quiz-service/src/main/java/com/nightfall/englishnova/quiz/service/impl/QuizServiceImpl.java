package com.nightfall.englishnova.quiz.service.impl;

import com.nightfall.englishnova.quiz.domain.po.AttemptPo;
import com.nightfall.englishnova.quiz.domain.vo.AttemptVo;
import com.nightfall.englishnova.quiz.domain.vo.EntryVo;
import com.nightfall.englishnova.quiz.domain.vo.PublicWordbookSubscriptionVo;
import com.nightfall.englishnova.quiz.domain.vo.QuestionVo;
import com.nightfall.englishnova.quiz.domain.vo.SessionVo;
import com.nightfall.englishnova.quiz.domain.vo.VocabularyEntryVo;
import com.nightfall.englishnova.quiz.domain.vo.WordbookProgressVo;
import com.nightfall.englishnova.quiz.domain.vo.WordbookSummaryVo;
import com.nightfall.englishnova.quiz.mapper.QuizAttemptMapper;
import com.nightfall.englishnova.quiz.mapper.QuizPublicWordbookMapper;
import com.nightfall.englishnova.quiz.mapper.QuizSessionMapper;
import com.nightfall.englishnova.quiz.mapper.QuizUserWordProgressMapper;
import com.nightfall.englishnova.quiz.mapper.QuizVocabularyEntryMapper;
import com.nightfall.englishnova.quiz.mapper.QuizWordbookMapper;
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
import com.nightfall.englishnova.shared.enums.QuizTargetType;
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
    private final QuizPublicWordbookMapper publicWordbookMapper;
    private final QuizSessionMapper sessionMapper;
    private final QuizAttemptMapper attemptMapper;
    private final QuizUserWordProgressMapper progressMapper;

    public QuizServiceImpl(
            QuizWordbookMapper wordbookMapper,
            QuizVocabularyEntryMapper vocabularyEntryMapper,
            QuizPublicWordbookMapper publicWordbookMapper,
            QuizSessionMapper sessionMapper,
            QuizAttemptMapper attemptMapper,
            QuizUserWordProgressMapper progressMapper
    ) {
        this.wordbookMapper = wordbookMapper;
        this.vocabularyEntryMapper = vocabularyEntryMapper;
        this.publicWordbookMapper = publicWordbookMapper;
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
        QuizTargetType targetType = request.targetType() == null ? QuizTargetType.USER_WORDBOOK : request.targetType();
        long targetId = request.targetId() == null ? 0L : request.targetId();
        QuizMode mode = request.mode() == null ? QuizMode.MIXED : request.mode();

        SessionSeed seed = resolveSessionSeed(user.id(), targetType, targetId);
        String sessionId = UUID.randomUUID().toString();

        sessionMapper.cancelActiveSessions(user.id(), targetType.name(), targetId);
        sessionMapper.insertSession(
                sessionId,
                user.id(),
                targetType.name(),
                targetId,
                mode.name(),
                seed.startOffset(),
                seed.totalQuestions(),
                Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant())
        );
        return getSessionState(user, sessionId);
    }

    @Override
    public QuizSessionStateDto getSessionState(CurrentUser user, String sessionId) {
        SessionVo session = requireSession(user.id(), sessionId);
        requireActiveSession(session);
        QuizQuestionDto currentQuestion = loadOrCreateCurrentQuestion(session);
        if (currentQuestion == null && "ACTIVE".equals(session.getStatus())) {
            sessionMapper.completeSession(sessionId);
            session = requireSession(user.id(), sessionId);
        }
        return new QuizSessionStateDto(mapSession(session), currentQuestion);
    }

    @Override
    @Transactional
    public QuizAnswerResultDto answer(CurrentUser user, String sessionId, QuizAnswerRequest request) {
        SessionVo session = requireSession(user.id(), sessionId);
        requireActiveSession(session);
        AttemptVo attempt = requireAttempt(user.id(), sessionId, request.attemptId());
        if (attempt.getSelectedOption() != null) {
            throw new IllegalArgumentException("This question has already been answered");
        }

        String selectedOption = request.selectedOption() == null ? "" : request.selectedOption().trim();
        boolean correct = attempt.getCorrectOption().equalsIgnoreCase(selectedOption);
        QuizTargetType targetType = QuizTargetType.valueOf(session.getTargetType());

        if (correct) {
            attemptMapper.markSelected(attempt.getId(), selectedOption, true);
            if (targetType == QuizTargetType.USER_WORDBOOK) {
                progressMapper.updateAfterAnswer(
                        user.id(),
                        requireUserEntryId(attempt),
                        1,
                        0,
                        ProgressStatus.CLEARED.name()
                );
            } else {
                publicWordbookMapper.advanceAfterCorrect(user.id(), session.getTargetId());
            }
            sessionMapper.markCorrectAnswer(sessionId);
        } else {
            attemptMapper.recordWrongSubmission(attempt.getId());
            if (targetType == QuizTargetType.USER_WORDBOOK) {
                progressMapper.updateAfterAnswer(
                        user.id(),
                        requireUserEntryId(attempt),
                        0,
                        1,
                        ProgressStatus.IN_PROGRESS.name()
                );
            } else {
                int inserted = publicWordbookMapper.insertWrongEntry(
                        user.id(),
                        session.getTargetId(),
                        requirePublicEntryId(attempt)
                );
                if (inserted > 0) {
                    publicWordbookMapper.incrementWrongCount(user.id(), session.getTargetId());
                }
            }
        }

        SessionVo refreshedSession = requireSession(user.id(), sessionId);
        QuizQuestionDto nextQuestion = loadOrCreateCurrentQuestion(refreshedSession);
        if (nextQuestion == null && "ACTIVE".equals(refreshedSession.getStatus())) {
            sessionMapper.completeSession(sessionId);
            refreshedSession = requireSession(user.id(), sessionId);
        }

        int remaining = Math.max(0, refreshedSession.getTotalQuestions() - refreshedSession.getAnsweredQuestions());
        return new QuizAnswerResultDto(
                correct,
                attempt.getCorrectOption(),
                remaining,
                mapSession(refreshedSession),
                nextQuestion
        );
    }

    private SessionSeed resolveSessionSeed(long userId, QuizTargetType targetType, long targetId) {
        return switch (targetType) {
            case USER_WORDBOOK -> {
                requireWordbook(userId, targetId);
                WordbookProgressVo progress = wordbookMapper.loadProgress(userId, targetId);
                int totalQuestions = progress == null ? 0 : progress.getWordCount();
                if (totalQuestions <= 0) {
                    throw new IllegalArgumentException("The current wordbook has no available words");
                }
                yield new SessionSeed(0, totalQuestions);
            }
            case PUBLIC_WORDBOOK -> {
                PublicWordbookSubscriptionVo subscription = requirePublicWordbookSubscription(userId, targetId);
                int remaining = Math.max(0, subscription.getWordCount() - subscription.getCurrentSortOrder());
                if (remaining <= 0) {
                    throw new IllegalArgumentException("This public wordbook is already completed. Reset progress to study again");
                }
                yield new SessionSeed(subscription.getCurrentSortOrder(), remaining);
            }
        };
    }

    private QuizQuestionDto loadOrCreateCurrentQuestion(SessionVo session) {
        if (!"ACTIVE".equals(session.getStatus())) {
            return null;
        }
        QuestionVo current = attemptMapper.loadCurrentQuestion(session.getId());
        if (current != null) {
            return mapQuestion(current, session);
        }
        if (session.getAnsweredQuestions() >= session.getTotalQuestions()) {
            return null;
        }

        QuizTargetType targetType = QuizTargetType.valueOf(session.getTargetType());
        EntryVo entry = resolveNextEntry(session, targetType);
        if (entry == null) {
            return null;
        }

        PromptType promptType = resolvePromptType(QuizMode.valueOf(session.getMode()), session.getAnsweredQuestions());
        AttemptPayload payload = buildAttemptPayload(targetType, session.getUserId(), entry, promptType);
        attemptMapper.insertAttempt(toAttemptInsertRow(session, targetType, entry, promptType, payload));

        QuestionVo inserted = attemptMapper.loadCurrentQuestion(session.getId());
        if (inserted == null) {
            throw new IllegalStateException("Failed to create the next quiz question");
        }
        return mapQuestion(inserted, session);
    }

    private EntryVo resolveNextEntry(SessionVo session, QuizTargetType targetType) {
        return switch (targetType) {
            case USER_WORDBOOK -> vocabularyEntryMapper.loadWordbookEntryByOffset(
                    session.getUserId(),
                    session.getTargetId(),
                    session.getAnsweredQuestions()
            );
            case PUBLIC_WORDBOOK -> vocabularyEntryMapper.loadPublicWordbookEntryBySortOrder(
                    session.getTargetId(),
                    session.getStartOffset() + session.getAnsweredQuestions() + 1
            );
        };
    }

    private QuizQuestionDto mapQuestion(QuestionVo row, SessionVo session) {
        return new QuizQuestionDto(
                row.getId(),
                PromptType.valueOf(row.getPromptType()),
                row.getPromptText(),
                List.of(row.getOptionA(), row.getOptionB(), row.getOptionC(), row.getOptionD()),
                session.getAnsweredQuestions() + 1,
                session.getTotalQuestions()
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

    private AttemptPayload buildAttemptPayload(QuizTargetType targetType, long userId, EntryVo entry, PromptType promptType) {
        String correctOption = promptType == PromptType.CN_TO_EN
                ? TextRepairUtils.repair(entry.getWord())
                : UserFacingTextNormalizer.normalizeMeaningText(entry.getMeaningCn());
        String promptText = promptType == PromptType.CN_TO_EN
                ? UserFacingTextNormalizer.normalizeMeaningText(entry.getMeaningCn())
                : TextRepairUtils.repair(entry.getWord());
        List<String> distractors = loadDistractors(targetType, userId, entry.getId(), promptType, correctOption);
        List<String> options = new ArrayList<>(distractors);
        options.add(correctOption);
        Collections.shuffle(options);
        return new AttemptPayload(promptText, options, correctOption);
    }

    private AttemptPo toAttemptInsertRow(
            SessionVo session,
            QuizTargetType targetType,
            EntryVo entry,
            PromptType promptType,
            AttemptPayload payload
    ) {
        AttemptPo row = new AttemptPo();
        row.setSessionId(session.getId());
        row.setUserId(session.getUserId());
        row.setUserVocabularyEntryId(targetType == QuizTargetType.USER_WORDBOOK ? entry.getId() : null);
        row.setPublicEntryId(targetType == QuizTargetType.PUBLIC_WORDBOOK ? entry.getId() : null);
        row.setPromptType(promptType.name());
        row.setPromptText(payload.promptText());
        row.setOptionA(payload.options().get(0));
        row.setOptionB(payload.options().get(1));
        row.setOptionC(payload.options().get(2));
        row.setOptionD(payload.options().get(3));
        row.setCorrectOption(payload.correctOption());
        row.setWrongSubmissions(0);
        row.setCreatedAt(Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));
        return row;
    }

    private List<String> loadDistractors(
            QuizTargetType targetType,
            long userId,
            long entryId,
            PromptType promptType,
            String correctOption
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (targetType == QuizTargetType.USER_WORDBOOK) {
            List<String> userCandidates = promptType == PromptType.CN_TO_EN
                    ? vocabularyEntryMapper.loadUserWordDistractors(userId, entryId)
                    : vocabularyEntryMapper.loadUserMeaningDistractors(userId, entryId);
            addCandidates(values, userCandidates, correctOption);
        }

        if (values.size() < 3) {
            List<String> publicCandidates = promptType == PromptType.CN_TO_EN
                    ? vocabularyEntryMapper.loadPublicWordDistractors(entryId)
                    : vocabularyEntryMapper.loadPublicMeaningDistractors(entryId);
            addCandidates(values, publicCandidates, correctOption);
        }

        if (values.size() < 3) {
            throw new IllegalArgumentException("Not enough distractors to generate a four-choice question");
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
            throw new NotFoundException("Quiz session not found");
        }
        return session;
    }

    private void requireActiveSession(SessionVo session) {
        if ("CANCELLED".equals(session.getStatus())) {
            throw new NotFoundException("Quiz session is no longer active");
        }
    }

    private AttemptVo requireAttempt(long userId, String sessionId, long attemptId) {
        AttemptVo attempt = attemptMapper.findByUserSessionAndId(userId, sessionId, attemptId);
        if (attempt == null) {
            throw new NotFoundException("Quiz question not found");
        }
        return attempt;
    }

    private void requireWordbook(long userId, long wordbookId) {
        if (wordbookMapper.countOwnedWordbook(userId, wordbookId) == 0) {
            throw new ForbiddenException("You cannot access this wordbook");
        }
    }

    private PublicWordbookSubscriptionVo requirePublicWordbookSubscription(long userId, long publicWordbookId) {
        PublicWordbookSubscriptionVo subscription = publicWordbookMapper.findSubscription(userId, publicWordbookId);
        if (subscription == null) {
            throw new ForbiddenException("You have not subscribed to this public wordbook");
        }
        return subscription;
    }

    private long requireUserEntryId(AttemptVo attempt) {
        if (attempt.getUserVocabularyEntryId() == null) {
            throw new IllegalStateException("Missing user entry id");
        }
        return attempt.getUserVocabularyEntryId();
    }

    private long requirePublicEntryId(AttemptVo attempt) {
        if (attempt.getPublicEntryId() == null) {
            throw new IllegalStateException("Missing public entry id");
        }
        return attempt.getPublicEntryId();
    }

    private QuizSessionDto mapSession(SessionVo row) {
        QuizTargetType targetType = QuizTargetType.valueOf(row.getTargetType());
        return new QuizSessionDto(
                row.getId(),
                row.getTargetId(),
                targetType,
                row.getTargetId(),
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

    private record SessionSeed(
            int startOffset,
            int totalQuestions
    ) {
    }
}
