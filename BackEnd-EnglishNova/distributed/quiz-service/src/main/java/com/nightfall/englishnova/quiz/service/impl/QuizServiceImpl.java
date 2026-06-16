package com.nightfall.englishnova.quiz.service.impl;

import com.nightfall.englishnova.quiz.domain.po.AttemptPo;
import com.nightfall.englishnova.quiz.domain.po.WordNotebookEntryPo;
import com.nightfall.englishnova.quiz.domain.po.WordNotebookPo;
import com.nightfall.englishnova.quiz.domain.vo.AttemptVo;
import com.nightfall.englishnova.quiz.domain.vo.EntryVo;
import com.nightfall.englishnova.quiz.domain.vo.PublicWordbookSubscriptionVo;
import com.nightfall.englishnova.quiz.domain.vo.QuestionVo;
import com.nightfall.englishnova.quiz.domain.vo.SessionVo;
import com.nightfall.englishnova.quiz.domain.vo.TodayAnswerStatsVo;
import com.nightfall.englishnova.quiz.domain.vo.VocabularyEntryVo;
import com.nightfall.englishnova.quiz.domain.vo.WordNotebookEntryVo;
import com.nightfall.englishnova.quiz.domain.vo.WordNotebookSummaryVo;
import com.nightfall.englishnova.quiz.domain.vo.WordbookProgressVo;
import com.nightfall.englishnova.quiz.domain.vo.WordbookSummaryVo;
import com.nightfall.englishnova.quiz.mapper.QuizAttemptMapper;
import com.nightfall.englishnova.quiz.mapper.QuizPublicWordbookMapper;
import com.nightfall.englishnova.quiz.mapper.QuizSessionMapper;
import com.nightfall.englishnova.quiz.mapper.QuizUserWordProgressMapper;
import com.nightfall.englishnova.quiz.mapper.QuizVocabularyEntryMapper;
import com.nightfall.englishnova.quiz.mapper.QuizWordNotebookMapper;
import com.nightfall.englishnova.quiz.mapper.QuizWordbookMapper;
import com.nightfall.englishnova.quiz.service.QuizService;
import com.nightfall.englishnova.quiz.utools.QuizTextUtools;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.AddWordNotebookEntryRequest;
import com.nightfall.englishnova.shared.dto.AddWordNotebookEntryResultDto;
import com.nightfall.englishnova.shared.dto.CreateQuizSessionRequest;
import com.nightfall.englishnova.shared.dto.CreateWordNotebookRequest;
import com.nightfall.englishnova.shared.dto.PublicWordbookProgressSnapshotDto;
import com.nightfall.englishnova.shared.dto.QuizAnswerRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerResultDto;
import com.nightfall.englishnova.shared.dto.QuizOptionDto;
import com.nightfall.englishnova.shared.dto.QuizQuestionDto;
import com.nightfall.englishnova.shared.dto.QuizSessionDto;
import com.nightfall.englishnova.shared.dto.QuizSessionStateDto;
import com.nightfall.englishnova.shared.dto.VocabularyEntryDto;
import com.nightfall.englishnova.shared.dto.WordNotebookEntryDto;
import com.nightfall.englishnova.shared.dto.WordNotebookSummaryDto;
import com.nightfall.englishnova.shared.dto.WordbookProgressDto;
import com.nightfall.englishnova.shared.dto.WordbookSummaryDto;
import com.nightfall.englishnova.shared.enums.ProgressStatus;
import com.nightfall.englishnova.shared.enums.PromptType;
import com.nightfall.englishnova.shared.enums.QuizMode;
import com.nightfall.englishnova.shared.enums.QuizOptionStrategy;
import com.nightfall.englishnova.shared.enums.QuizTargetType;
import com.nightfall.englishnova.shared.enums.VocabularyEntryType;
import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import com.nightfall.englishnova.shared.exception.ForbiddenException;
import com.nightfall.englishnova.shared.exception.NotFoundException;
import com.nightfall.englishnova.shared.text.TextRepairUtils;
import com.nightfall.englishnova.shared.text.UserFacingTextNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 测验领域服务，负责词书/单词本入口、测验会话生命周期、答题判定和进度回写。
 */
@Service
public class QuizServiceImpl implements QuizService {

    private final QuizWordbookMapper wordbookMapper;
    private final QuizVocabularyEntryMapper vocabularyEntryMapper;
    private final QuizPublicWordbookMapper publicWordbookMapper;
    private final QuizSessionMapper sessionMapper;
    private final QuizAttemptMapper attemptMapper;
    private final QuizUserWordProgressMapper progressMapper;
    private final QuizWordNotebookMapper wordNotebookMapper;

    public QuizServiceImpl(
            QuizWordbookMapper wordbookMapper,
            QuizVocabularyEntryMapper vocabularyEntryMapper,
            QuizPublicWordbookMapper publicWordbookMapper,
            QuizSessionMapper sessionMapper,
            QuizAttemptMapper attemptMapper,
            QuizUserWordProgressMapper progressMapper,
            QuizWordNotebookMapper wordNotebookMapper
    ) {
        this.wordbookMapper = wordbookMapper;
        this.vocabularyEntryMapper = vocabularyEntryMapper;
        this.publicWordbookMapper = publicWordbookMapper;
        this.sessionMapper = sessionMapper;
        this.attemptMapper = attemptMapper;
        this.progressMapper = progressMapper;
        this.wordNotebookMapper = wordNotebookMapper;
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
    public List<WordNotebookSummaryDto> listWordNotebooks(CurrentUser user, String word) {
        String normalizedWord = normalizeNotebookWord(word);
        return wordNotebookMapper.listWordNotebooks(user.id(), normalizedWord).stream()
                .map(this::mapWordNotebookSummary)
                .toList();
    }

    @Override
    @Transactional
    public WordNotebookSummaryDto createWordNotebook(CurrentUser user, CreateWordNotebookRequest request) {
        WordNotebookPo row = new WordNotebookPo();
        row.setUserId(user.id());
        row.setName(normalizeNotebookName(request.name()));
        row.setCreatedAt(Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));
        wordNotebookMapper.insertWordNotebook(row);
        if (row.getId() == null) {
            throw new IllegalStateException("Failed to create word notebook");
        }

        WordNotebookSummaryVo summary = wordNotebookMapper.loadWordNotebookSummary(user.id(), row.getId(), null);
        if (summary == null) {
            throw new IllegalStateException("Failed to load created word notebook");
        }
        return mapWordNotebookSummary(summary);
    }

    @Override
    public List<WordNotebookEntryDto> listWordNotebookEntries(CurrentUser user, long notebookId) {
        requireWordNotebook(user.id(), notebookId);
        return wordNotebookMapper.listWordNotebookEntries(user.id(), notebookId).stream()
                .map(this::mapWordNotebookEntry)
                .toList();
    }

    @Override
    @Transactional
    public AddWordNotebookEntryResultDto addWordNotebookEntry(CurrentUser user, long notebookId, AddWordNotebookEntryRequest request) {
        requireWordNotebook(user.id(), notebookId);

        String normalizedWord = normalizeNotebookWord(request.word());
        if (normalizedWord.isBlank()) {
            throw new IllegalArgumentException("Word cannot be empty");
        }

        WordNotebookEntryPo row = new WordNotebookEntryPo();
        row.setWordNotebookId(notebookId);
        row.setNormalizedWord(normalizedWord);
        row.setWord(valueOrEmpty(request.word()).trim());
        row.setPhonetic(valueOrEmpty(request.phonetic()).trim());
        row.setMeaningCn(valueOrEmpty(request.meaningCn()).trim());
        row.setExampleSentence(valueOrEmpty(request.exampleSentence()).trim());
        row.setCorrectedExampleSentence(valueOrEmpty(request.correctedExampleSentence()).trim());
        row.setChineseSentence(valueOrEmpty(request.chineseSentence()).trim());
        row.setExampleAudioUrl(normalizeOptionalUrl(request.exampleAudioUrl()));
        applySavedNotebookOptions(row, request);
        row.setSourceEntryType(request.sourceEntryType() == null ? null : request.sourceEntryType().name());
        row.setSourceEntryId(request.sourceEntryId());
        row.setCreatedAt(Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));

        int affectedRows = wordNotebookMapper.upsertWordNotebookEntry(row);
        if (row.getId() == null) {
            throw new IllegalStateException("Failed to save word notebook entry");
        }

        wordNotebookMapper.touchWordNotebook(user.id(), notebookId);

        WordNotebookEntryVo entry = wordNotebookMapper.loadWordNotebookEntry(user.id(), row.getId());
        WordNotebookSummaryVo summary = wordNotebookMapper.loadWordNotebookSummary(user.id(), notebookId, normalizedWord);
        if (entry == null || summary == null) {
            throw new IllegalStateException("Failed to load saved word notebook entry");
        }

        return new AddWordNotebookEntryResultDto(
                affectedRows == 1,
                mapWordNotebookSummary(summary),
                mapWordNotebookEntry(entry)
        );
    }

    @Override
    @Transactional
    public int removeWordNotebookEntries(CurrentUser user, String word) {
        String normalizedWord = normalizeNotebookWord(word);
        if (normalizedWord.isBlank()) {
            throw new IllegalArgumentException("Word cannot be empty");
        }
        List<Long> notebookIds = wordNotebookMapper.listWordNotebookIdsByNormalizedWord(user.id(), normalizedWord);
        if (notebookIds.isEmpty()) {
            return 0;
        }
        int removedCount = wordNotebookMapper.deleteWordNotebookEntriesByNormalizedWord(user.id(), normalizedWord);
        for (Long notebookId : notebookIds) {
            if (notebookId != null) {
                wordNotebookMapper.touchWordNotebook(user.id(), notebookId);
            }
        }
        return removedCount;
    }

    @Override
    @Transactional
    public QuizSessionStateDto createSession(CurrentUser user, CreateQuizSessionRequest request) {
        // 同一目标只保留一个活动会话，避免进度统计出现歧义。
        QuizTargetType targetType = request.targetType() == null ? QuizTargetType.USER_WORDBOOK : request.targetType();
        long targetId = request.targetId() == null ? 0L : request.targetId();
        QuizMode mode = targetType == QuizTargetType.PUBLIC_WORDBOOK
                ? QuizMode.EN_TO_CN
                : (request.mode() == null ? QuizMode.MIXED : request.mode());

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
        // 查询会话状态时允许懒加载下一题，使刷新页面和继续练习复用同一流程。
        QuizQuestionDto currentQuestion = loadOrCreateCurrentQuestion(session);
        if (currentQuestion == null && "ACTIVE".equals(session.getStatus())) {
            sessionMapper.completeSession(sessionId);
            session = requireSession(user.id(), sessionId);
        }
        return new QuizSessionStateDto(mapSession(session), currentQuestion);
    }

    @Override
    @Transactional
    public QuizSessionStateDto refreshQuestionOptions(CurrentUser user, String sessionId, long attemptId) {
        SessionVo session = requireSession(user.id(), sessionId);
        requireActiveSession(session);
        QuestionVo current = attemptMapper.loadCurrentQuestion(sessionId);
        if (current == null || current.getId() != attemptId) {
            throw new NotFoundException("Quiz question is no longer current");
        }

        QuizTargetType targetType = QuizTargetType.valueOf(session.getTargetType());
        EntryVo entry = resolveQuestionEntry(user.id(), session, targetType, current);
        PromptType promptType = PromptType.valueOf(current.getPromptType());
        AttemptPayload payload = buildAttemptPayload(
                targetType,
                user.id(),
                session.getTargetId(),
                entry,
                promptType,
                resolveQuizOptionStrategy(user.id(), targetType)
        );

        attemptMapper.updateOptions(
                attemptId,
                payload.options().get(0).value(),
                payload.options().get(0).word(),
                payload.options().get(0).meaningCn(),
                payload.options().get(1).value(),
                payload.options().get(1).word(),
                payload.options().get(1).meaningCn(),
                payload.options().get(2).value(),
                payload.options().get(2).word(),
                payload.options().get(2).meaningCn(),
                payload.options().get(3).value(),
                payload.options().get(3).word(),
                payload.options().get(3).meaningCn(),
                payload.correctOption(),
                payload.promptText()
        );

        QuestionVo refreshed = attemptMapper.loadCurrentQuestion(sessionId);
        return new QuizSessionStateDto(mapSession(session), refreshed == null ? null : mapQuestion(refreshed, session, entry));
    }

    @Override
    @Transactional
    public QuizAnswerResultDto answer(CurrentUser user, String sessionId, QuizAnswerRequest request) {
        // 答题提交需要同时更新题目状态、会话计数和学习进度，因此保持事务边界。
        SessionVo session = requireSession(user.id(), sessionId);
        requireActiveSession(session);
        AttemptVo attempt = requireAttempt(user.id(), sessionId, request.attemptId());
        if (attempt.getSelectedOption() != null) {
            return buildAlreadyAnsweredResult(user, session, attempt);
        }

        String selectedOption = request.selectedOption() == null ? "" : request.selectedOption().trim();
        boolean correct = attempt.getCorrectOption().equalsIgnoreCase(selectedOption);
        boolean firstTryCorrect = correct && attempt.getWrongSubmissions() == 0;
        QuizTargetType targetType = QuizTargetType.valueOf(session.getTargetType());
        PublicWordbookProgressSnapshotDto publicWordbookProgress = null;
        boolean dailyTargetJustCompleted = false;

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
            } else if (targetType == QuizTargetType.PUBLIC_WORDBOOK) {
                publicWordbookMapper.advanceAfterCorrect(user.id(), session.getTargetId());
                PublicWordbookSubscriptionVo updatedSubscription = requirePublicWordbookSubscription(user.id(), session.getTargetId());
                publicWordbookProgress = mapPublicWordbookProgress(updatedSubscription);
                dailyTargetJustCompleted =
                        updatedSubscription.getDailyTargetCount() > 0
                                && updatedSubscription.getTodayCompletedCount() >= updatedSubscription.getDailyTargetCount()
                                && updatedSubscription.getTodayCompletedCount() - 1 < updatedSubscription.getDailyTargetCount();
            }
            sessionMapper.markAnswered(sessionId, firstTryCorrect ? 1 : 0);
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
            } else if (targetType == QuizTargetType.PUBLIC_WORDBOOK) {
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

        if (targetType == QuizTargetType.PUBLIC_WORDBOOK && publicWordbookProgress == null) {
            publicWordbookProgress = mapPublicWordbookProgress(requirePublicWordbookSubscription(user.id(), session.getTargetId()));
        }

        SessionVo refreshedSession = requireSession(user.id(), sessionId);
        if (correct
                && refreshedSession.getAnsweredQuestions() >= refreshedSession.getTotalQuestions()
                && "ACTIVE".equals(refreshedSession.getStatus())) {
            sessionMapper.completeSession(sessionId);
            refreshedSession = requireSession(user.id(), sessionId);
        }

        int remaining = Math.max(0, refreshedSession.getTotalQuestions() - refreshedSession.getAnsweredQuestions());
        return new QuizAnswerResultDto(
                correct,
                attempt.getCorrectOption(),
                correct ? attempt.getCorrectOption() : selectedOption,
                remaining,
                dailyTargetJustCompleted,
                publicWordbookProgress,
                mapSession(refreshedSession),
                null
        );
    }

    private QuizAnswerResultDto buildAlreadyAnsweredResult(CurrentUser user, SessionVo session, AttemptVo attempt) {
        QuizTargetType targetType = QuizTargetType.valueOf(session.getTargetType());
        PublicWordbookProgressSnapshotDto publicWordbookProgress = targetType == QuizTargetType.PUBLIC_WORDBOOK
                ? mapPublicWordbookProgress(requirePublicWordbookSubscription(user.id(), session.getTargetId()))
                : null;
        SessionVo refreshedSession = requireSession(user.id(), session.getId());
        return new QuizAnswerResultDto(
                true,
                attempt.getCorrectOption(),
                attempt.getSelectedOption(),
                Math.max(0, refreshedSession.getTotalQuestions() - refreshedSession.getAnsweredQuestions()),
                false,
                publicWordbookProgress,
                mapSession(refreshedSession),
                null
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
                if (subscription.getDailyTargetCount() <= 0) {
                    throw new IllegalArgumentException("请先设置每日背词数量");
                }
                int remainingTotal = Math.max(0, subscription.getWordCount() - subscription.getCurrentSortOrder());
                if (remainingTotal <= 0) {
                    throw new IllegalArgumentException("这本公共词书已经完成，请先重置进度");
                }
                int todayRemaining = Math.max(0, subscription.getDailyTargetCount() - subscription.getTodayCompletedCount());
                if (todayRemaining <= 0) {
                    throw new IllegalArgumentException("今日背词目标已完成，明天再来");
                }
                yield new SessionSeed(subscription.getCurrentSortOrder(), Math.min(remainingTotal, todayRemaining));
            }
            case WORD_NOTEBOOK -> {
                requireWordNotebook(userId, targetId);
                WordNotebookSummaryVo notebook = wordNotebookMapper.loadWordNotebookSummary(userId, targetId, null);
                int totalQuestions = notebook == null ? 0 : notebook.getWordCount();
                if (totalQuestions <= 0) {
                    throw new IllegalArgumentException("The current word notebook has no available words");
                }
                yield new SessionSeed(0, totalQuestions);
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

        PromptType promptType = resolvePromptType(
                QuizMode.valueOf(session.getMode()),
                session.getAnsweredQuestions(),
                targetType
        );
        AttemptPayload payload = buildAttemptPayload(
                targetType,
                session.getUserId(),
                session.getTargetId(),
                entry,
                promptType,
                resolveQuizOptionStrategy(session.getUserId(), targetType)
        );
        attemptMapper.insertAttempt(toAttemptInsertRow(session, targetType, entry, promptType, payload));

        QuestionVo inserted = attemptMapper.loadCurrentQuestion(session.getId());
        if (inserted == null) {
            throw new IllegalStateException("Failed to create the next quiz question");
        }
        return mapQuestion(inserted, session, entry);
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
            case WORD_NOTEBOOK -> vocabularyEntryMapper.loadWordNotebookEntryByOffset(
                    session.getUserId(),
                    session.getTargetId(),
                    session.getAnsweredQuestions()
            );
        };
    }

    private EntryVo resolveQuestionEntry(long userId, SessionVo session, QuizTargetType targetType, QuestionVo question) {
        EntryVo entry = switch (targetType) {
            case USER_WORDBOOK -> {
                if (question.getUserVocabularyEntryId() == null) {
                    yield null;
                }
                yield vocabularyEntryMapper.loadUserEntryById(userId, question.getUserVocabularyEntryId());
            }
            case PUBLIC_WORDBOOK -> {
                if (question.getPublicEntryId() == null) {
                    yield null;
                }
                yield vocabularyEntryMapper.loadPublicEntryById(session.getTargetId(), question.getPublicEntryId());
            }
            case WORD_NOTEBOOK -> {
                if (question.getWordNotebookEntryId() == null) {
                    yield null;
                }
                yield vocabularyEntryMapper.loadWordNotebookEntryById(userId, question.getWordNotebookEntryId());
            }
        };
        if (entry == null) {
            throw new NotFoundException("Quiz question entry not found");
        }
        return entry;
    }

    private QuizQuestionDto mapQuestion(QuestionVo row, SessionVo session) {
        return mapQuestion(row, session, null);
    }

    private QuizQuestionDto mapQuestion(QuestionVo row, SessionVo session, EntryVo fallbackEntry) {
        EntryVo resolvedEntry = fallbackEntry;
        if (resolvedEntry == null
                && QuizTargetType.PUBLIC_WORDBOOK.name().equals(session.getTargetType())
                && row.getPublicEntryId() != null) {
            resolvedEntry = vocabularyEntryMapper.loadPublicEntryById(session.getTargetId(), row.getPublicEntryId());
        }
        if (resolvedEntry == null
                && QuizTargetType.WORD_NOTEBOOK.name().equals(session.getTargetType())
                && row.getWordNotebookEntryId() != null) {
            resolvedEntry = vocabularyEntryMapper.loadWordNotebookEntryById(session.getUserId(), row.getWordNotebookEntryId());
        }
        if (resolvedEntry != null && QuizTargetType.WORD_NOTEBOOK.name().equals(session.getTargetType())) {
            resolvedEntry = hydrateNotebookEntryFromSource(session.getUserId(), resolvedEntry);
        }
        String phonetic = row.getPhonetic();
        if ((phonetic == null || phonetic.isBlank()) && resolvedEntry != null) {
            phonetic = resolvedEntry.getPhonetic();
        }
        String audioUrl = row.getAudioUrl();
        if ((audioUrl == null || audioUrl.isBlank()) && resolvedEntry != null) {
            audioUrl = resolvedEntry.getAudioUrl();
        }
        return new QuizQuestionDto(
                row.getId(),
                PromptType.valueOf(row.getPromptType()),
                row.getPromptText(),
                resolveCurrentWord(row),
                valueOrEmpty(resolvedEntry == null ? null : resolvedEntry.getMeaningCn()),
                QuizTextUtools.normalizePhonetic(phonetic),
                toClientAudioUrl(audioUrl),
                valueOrEmpty(resolvedEntry == null ? null : resolvedEntry.getExampleSentence()),
                valueOrEmpty(resolvedEntry == null ? null : resolvedEntry.getCorrectedExampleSentence()),
                valueOrEmpty(resolvedEntry == null ? null : resolvedEntry.getChineseSentence()),
                toClientExampleAudioUrl(row.getPublicEntryId(), resolvedEntry == null ? null : resolvedEntry.getExampleAudioUrl()),
                resolveQuestionSourceEntryType(session, resolvedEntry),
                resolveQuestionSourceEntryId(row, session, resolvedEntry),
                List.of(row.getOptionA(), row.getOptionB(), row.getOptionC(), row.getOptionD()),
                buildOptionDetails(row, session),
                session.getAnsweredQuestions() + 1,
                session.getTotalQuestions()
        );
    }

    private List<QuizOptionDto> buildOptionDetails(QuestionVo row, SessionVo session) {
        QuizTargetType targetType = QuizTargetType.valueOf(session.getTargetType());
        if (targetType == QuizTargetType.USER_WORDBOOK) {
            return List.of();
        }

        List<QuizOptionDto> persisted = List.of(
                toPersistedOptionDetail(row.getOptionA(), row.getOptionAWord(), row.getOptionAMeaningCn()),
                toPersistedOptionDetail(row.getOptionB(), row.getOptionBWord(), row.getOptionBMeaningCn()),
                toPersistedOptionDetail(row.getOptionC(), row.getOptionCWord(), row.getOptionCMeaningCn()),
                toPersistedOptionDetail(row.getOptionD(), row.getOptionDWord(), row.getOptionDMeaningCn())
        );
        if (targetType == QuizTargetType.WORD_NOTEBOOK) {
            return persisted;
        }
        boolean needsLegacyHydration = persisted.stream().anyMatch(detail -> detail.word() == null || detail.word().isBlank());
        if (!needsLegacyHydration) {
            return persisted;
        }

        Map<String, EntryVo> legacyEntriesByMeaning = new HashMap<>();
        for (EntryVo entry : vocabularyEntryMapper.loadPublicWordbookOptionEntries(session.getTargetId())) {
            String meaning = UserFacingTextNormalizer.normalizeMeaningText(entry.getMeaningCn()).trim();
            legacyEntriesByMeaning.putIfAbsent(meaning, entry);
        }

        List<QuizOptionDto> hydrated = new ArrayList<>(persisted.size());
        for (QuizOptionDto detail : persisted) {
            if (detail.word() != null && !detail.word().isBlank()) {
                hydrated.add(detail);
                continue;
            }
            EntryVo entry = legacyEntriesByMeaning.get(UserFacingTextNormalizer.normalizeMeaningText(detail.value()).trim());
            hydrated.add(new QuizOptionDto(
                    detail.value(),
                    entry == null ? "" : TextRepairUtils.repair(entry.getWord()),
                    entry == null ? detail.meaningCn() : UserFacingTextNormalizer.normalizeMeaningText(entry.getMeaningCn())
            ));
        }
        return hydrated;
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
                "",
                "",
                "",
                UserFacingTextNormalizer.normalizeMeaningText(row.getCategory()),
                row.getDifficulty(),
                row.getVisibility()
        );
    }

    private WordNotebookSummaryDto mapWordNotebookSummary(WordNotebookSummaryVo row) {
        return new WordNotebookSummaryDto(
                row.getId(),
                UserFacingTextNormalizer.normalizeDisplayText(row.getName()),
                row.getWordCount(),
                row.isContainsWord(),
                row.getCreatedAt().toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private WordNotebookEntryDto mapWordNotebookEntry(WordNotebookEntryVo row) {
        return new WordNotebookEntryDto(
                row.getId(),
                row.getNotebookId(),
                valueOrEmpty(row.getNormalizedWord()),
                valueOrEmpty(row.getWord()),
                valueOrEmpty(row.getPhonetic()),
                valueOrEmpty(row.getMeaningCn()),
                valueOrEmpty(row.getExampleSentence()),
                valueOrEmpty(row.getCorrectedExampleSentence()),
                valueOrEmpty(row.getChineseSentence()),
                normalizeOptionalUrl(row.getExampleAudioUrl()),
                row.getSourceEntryType() == null || row.getSourceEntryType().isBlank()
                        ? null
                        : VocabularyEntryType.valueOf(row.getSourceEntryType()),
                row.getSourceEntryId(),
                row.getCreatedAt().toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private PromptType resolvePromptType(QuizMode mode, int index, QuizTargetType targetType) {
        if (targetType == QuizTargetType.PUBLIC_WORDBOOK || targetType == QuizTargetType.WORD_NOTEBOOK) {
            return PromptType.EN_TO_CN;
        }
        return switch (mode) {
            case CN_TO_EN -> PromptType.CN_TO_EN;
            case EN_TO_CN -> PromptType.EN_TO_CN;
            case MIXED -> index % 2 == 0 ? PromptType.CN_TO_EN : PromptType.EN_TO_CN;
        };
    }

    private AttemptPayload buildAttemptPayload(
            QuizTargetType targetType,
            long userId,
            long targetId,
            EntryVo entry,
            PromptType promptType,
            QuizOptionStrategy optionStrategy
    ) {
        AttemptPayload storedPayload = buildStoredNotebookAttemptPayload(targetType, entry, promptType);
        if (storedPayload != null) {
            return storedPayload;
        }

        String promptText = promptType == PromptType.CN_TO_EN
                ? UserFacingTextNormalizer.normalizeMeaningText(entry.getMeaningCn())
                : TextRepairUtils.repair(entry.getWord());
        OptionPayload correctOption = toOptionPayload(entry, promptType);
        List<OptionPayload> distractors = loadDistractors(
                targetType,
                userId,
                targetId,
                entry,
                promptType,
                correctOption,
                optionStrategy
        );
        List<OptionPayload> options = new ArrayList<>(distractors);
        options.add(correctOption);
        Collections.shuffle(options);
        return new AttemptPayload(promptText, options, correctOption.value());
    }

    private AttemptPayload buildStoredNotebookAttemptPayload(
            QuizTargetType targetType,
            EntryVo entry,
            PromptType promptType
    ) {
        if (targetType != QuizTargetType.WORD_NOTEBOOK || promptType != PromptType.EN_TO_CN) {
            return null;
        }

        List<OptionPayload> storedOptions = new ArrayList<>(4);
        storedOptions.add(toStoredNotebookOption(entry.getOptionA(), entry.getOptionAWord(), entry.getOptionAMeaningCn()));
        storedOptions.add(toStoredNotebookOption(entry.getOptionB(), entry.getOptionBWord(), entry.getOptionBMeaningCn()));
        storedOptions.add(toStoredNotebookOption(entry.getOptionC(), entry.getOptionCWord(), entry.getOptionCMeaningCn()));
        storedOptions.add(toStoredNotebookOption(entry.getOptionD(), entry.getOptionDWord(), entry.getOptionDMeaningCn()));
        if (storedOptions.stream().anyMatch(option -> option == null || option.value().isBlank())) {
            return null;
        }

        String storedCorrectOption = UserFacingTextNormalizer.normalizeMeaningText(valueOrEmpty(entry.getCorrectOption())).trim();
        if (storedCorrectOption.isBlank()
                || storedOptions.stream().noneMatch(option -> option.value().equals(storedCorrectOption))) {
            return null;
        }

        String promptText = TextRepairUtils.repair(entry.getWord());
        return new AttemptPayload(promptText, storedOptions, storedCorrectOption);
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
        row.setWordNotebookEntryId(targetType == QuizTargetType.WORD_NOTEBOOK ? entry.getId() : null);
        row.setPublicEntryId(targetType == QuizTargetType.PUBLIC_WORDBOOK ? entry.getId() : null);
        row.setPromptType(promptType.name());
        row.setPromptText(payload.promptText());
        row.setOptionA(payload.options().get(0).value());
        row.setOptionAWord(payload.options().get(0).word());
        row.setOptionAMeaningCn(payload.options().get(0).meaningCn());
        row.setOptionB(payload.options().get(1).value());
        row.setOptionBWord(payload.options().get(1).word());
        row.setOptionBMeaningCn(payload.options().get(1).meaningCn());
        row.setOptionC(payload.options().get(2).value());
        row.setOptionCWord(payload.options().get(2).word());
        row.setOptionCMeaningCn(payload.options().get(2).meaningCn());
        row.setOptionD(payload.options().get(3).value());
        row.setOptionDWord(payload.options().get(3).word());
        row.setOptionDMeaningCn(payload.options().get(3).meaningCn());
        row.setCorrectOption(payload.correctOption());
        row.setWrongSubmissions(0);
        row.setCreatedAt(Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));
        return row;
    }

    private List<OptionPayload> loadDistractors(
            QuizTargetType targetType,
            long userId,
            long targetId,
            EntryVo entry,
            PromptType promptType,
            OptionPayload correctOption,
            QuizOptionStrategy optionStrategy
    ) {
        if (targetType == QuizTargetType.PUBLIC_WORDBOOK) {
            return loadPublicDistractors(targetId, entry, promptType, correctOption, optionStrategy);
        }
        if (optionStrategy == QuizOptionStrategy.SIMILAR) {
            return loadSimilarDistractors(targetType, userId, targetId, entry, promptType, correctOption);
        }
        if (optionStrategy == QuizOptionStrategy.RELATED) {
            return loadRelatedDistractors(targetType, userId, targetId, entry, promptType, correctOption);
        }
        return loadRandomDistractors(targetType, userId, targetId, entry.getId(), promptType, correctOption);
    }

    private List<OptionPayload> loadPublicDistractors(
            long targetId,
            EntryVo entry,
            PromptType promptType,
            OptionPayload correctOption,
            QuizOptionStrategy optionStrategy
    ) {
        if (optionStrategy == QuizOptionStrategy.SIMILAR) {
            return loadSimilarDistractors(QuizTargetType.PUBLIC_WORDBOOK, 0L, targetId, entry, promptType, correctOption);
        }
        if (optionStrategy == QuizOptionStrategy.RELATED) {
            return loadRelatedDistractors(QuizTargetType.PUBLIC_WORDBOOK, 0L, targetId, entry, promptType, correctOption);
        }
        return loadRandomDistractors(QuizTargetType.PUBLIC_WORDBOOK, 0L, targetId, entry.getId(), promptType, correctOption);
    }

    private List<OptionPayload> loadRandomDistractors(
            QuizTargetType targetType,
            long userId,
            long targetId,
            long entryId,
            PromptType promptType,
            OptionPayload correctOption
    ) {
        LinkedHashMap<String, OptionPayload> values = new LinkedHashMap<>();
        if (targetType == QuizTargetType.USER_WORDBOOK) {
            List<String> userCandidates = promptType == PromptType.CN_TO_EN
                    ? vocabularyEntryMapper.loadUserWordDistractors(userId, entryId)
                    : vocabularyEntryMapper.loadUserMeaningDistractors(userId, targetId, entryId);
            addCandidates(values, userCandidates, correctOption);
        }

        if (targetType == QuizTargetType.PUBLIC_WORDBOOK) {
            List<EntryVo> publicCandidates = vocabularyEntryMapper.loadPublicRandomDistractorEntries(targetId, entryId);
            addCandidateEntries(values, publicCandidates, promptType, correctOption);
        }

        if (targetType == QuizTargetType.WORD_NOTEBOOK) {
            List<EntryVo> notebookCandidates = vocabularyEntryMapper.loadWordNotebookRandomDistractorEntries(userId, targetId, entryId);
            addCandidateEntries(values, notebookCandidates, promptType, correctOption);
            if (values.size() < 3) {
                List<EntryVo> fallbackCandidates =
                        vocabularyEntryMapper.loadNotebookFallbackRandomDistractorEntries(correctOption.word());
                addCandidateEntries(values, fallbackCandidates, promptType, correctOption);
            }
        }

        if (values.size() < 3) {
            throw new IllegalArgumentException("当前词书至少需要 4 个不同选项才能生成四选一题目");
        }
        return new ArrayList<>(values.values()).subList(0, 3);
    }

    private List<OptionPayload> loadSimilarDistractors(
            QuizTargetType targetType,
            long userId,
            long targetId,
            EntryVo entry,
            PromptType promptType,
            OptionPayload correctOption
    ) {
        List<EntryVo> candidates = loadDistractorCandidates(targetType, userId, targetId, entry);
        candidates.sort(
                Comparator.comparingInt((EntryVo candidate) -> levenshtein(normalizeWord(entry.getWord()), normalizeWord(candidate.getWord())))
                        .thenComparingInt(candidate -> Math.abs(normalizeWord(entry.getWord()).length() - normalizeWord(candidate.getWord()).length()))
                        .thenComparingInt(candidate -> -commonPrefixLength(normalizeWord(entry.getWord()), normalizeWord(candidate.getWord())))
        );
        LinkedHashMap<String, OptionPayload> values = new LinkedHashMap<>();
        addCandidateEntries(values, candidates, promptType, correctOption);
        fillRandomDistractors(values, targetType, userId, targetId, entry.getId(), promptType, correctOption);
        return requireThreeDistractors(values);
    }

    private List<OptionPayload> loadRelatedDistractors(
            QuizTargetType targetType,
            long userId,
            long targetId,
            EntryVo entry,
            PromptType promptType,
            OptionPayload correctOption
    ) {
        List<EntryVo> candidates = loadDistractorCandidates(targetType, userId, targetId, entry);
        candidates.sort(Comparator.comparingInt((EntryVo candidate) -> relatedScore(entry, candidate)).reversed());
        LinkedHashMap<String, OptionPayload> values = new LinkedHashMap<>();
        for (EntryVo candidate : candidates) {
            if (values.size() >= 3) {
                break;
            }
            if (relatedScore(entry, candidate) <= 0) {
                continue;
            }
            addCandidate(values, toOptionPayload(candidate, promptType), correctOption);
        }
        fillRandomDistractors(values, targetType, userId, targetId, entry.getId(), promptType, correctOption);
        return requireThreeDistractors(values);
    }

    private List<EntryVo> loadDistractorCandidates(QuizTargetType targetType, long userId, long targetId, long entryId) {
        return switch (targetType) {
            case USER_WORDBOOK -> vocabularyEntryMapper.loadUserDistractorCandidates(userId, targetId, entryId);
            case PUBLIC_WORDBOOK -> vocabularyEntryMapper.loadPublicDistractorCandidates(targetId, entryId);
            case WORD_NOTEBOOK -> throw new IllegalStateException("Word notebook distractors should be loaded with the current entry");
        };
    }

    private List<EntryVo> loadDistractorCandidates(
            QuizTargetType targetType,
            long userId,
            long targetId,
            EntryVo entry
    ) {
        if (targetType != QuizTargetType.WORD_NOTEBOOK) {
            return loadDistractorCandidates(targetType, userId, targetId, entry.getId());
        }

        List<EntryVo> candidates = new ArrayList<>(
                vocabularyEntryMapper.loadWordNotebookDistractorCandidates(userId, targetId, entry.getId())
        );
        candidates.addAll(vocabularyEntryMapper.loadNotebookFallbackDistractorCandidates(entry.getWord()));
        return candidates;
    }

    private void addCandidateEntries(
            LinkedHashMap<String, OptionPayload> values,
            List<EntryVo> candidates,
            PromptType promptType,
            OptionPayload correctOption
    ) {
        for (EntryVo candidate : candidates) {
            if (values.size() >= 3) {
                return;
            }
            addCandidate(values, toOptionPayload(candidate, promptType), correctOption);
        }
    }

    private void fillRandomDistractors(
            LinkedHashMap<String, OptionPayload> values,
            QuizTargetType targetType,
            long userId,
            long targetId,
            long entryId,
            PromptType promptType,
            OptionPayload correctOption
    ) {
        if (values.size() >= 3) {
            return;
        }
        List<OptionPayload> fallback = loadRandomDistractors(targetType, userId, targetId, entryId, promptType, correctOption);
        addPayloadCandidates(values, fallback, correctOption);
    }

    private List<OptionPayload> requireThreeDistractors(LinkedHashMap<String, OptionPayload> values) {
        if (values.size() < 3) {
            throw new IllegalArgumentException("当前词书至少需要 4 个不同选项才能生成四选一题目");
        }
        return new ArrayList<>(values.values()).subList(0, 3);
    }

    private OptionPayload toOptionPayload(EntryVo entry, PromptType promptType) {
        String word = TextRepairUtils.repair(entry.getWord()).trim();
        String meaning = UserFacingTextNormalizer.normalizeMeaningText(entry.getMeaningCn()).trim();
        String value = promptType == PromptType.CN_TO_EN ? word : meaning;
        return new OptionPayload(value, word, meaning);
    }

    private OptionPayload toStoredNotebookOption(String value, String word, String meaningCn) {
        String normalizedValue = UserFacingTextNormalizer.normalizeMeaningText(valueOrEmpty(value)).trim();
        if (normalizedValue.isBlank()) {
            return null;
        }
        return new OptionPayload(
                normalizedValue,
                TextRepairUtils.repair(valueOrEmpty(word)).trim(),
                UserFacingTextNormalizer.normalizeMeaningText(
                        valueOrEmpty(meaningCn).isBlank() ? normalizedValue : meaningCn
                ).trim()
        );
    }

    private QuizOptionStrategy resolveUserQuizOptionStrategy(long userId) {
        String value = sessionMapper.loadUserQuizOptionStrategy(userId);
        if (value == null || value.isBlank()) {
            return QuizOptionStrategy.RANDOM;
        }
        try {
            return QuizOptionStrategy.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return QuizOptionStrategy.RANDOM;
        }
    }

    private QuizOptionStrategy resolveQuizOptionStrategy(long userId, QuizTargetType targetType) {
        return resolveUserQuizOptionStrategy(userId);
    }

    private VocabularyEntryType resolveQuestionSourceEntryType(SessionVo session, EntryVo resolvedEntry) {
        return switch (QuizTargetType.valueOf(session.getTargetType())) {
            case PUBLIC_WORDBOOK -> VocabularyEntryType.PUBLIC;
            case USER_WORDBOOK -> VocabularyEntryType.USER;
            case WORD_NOTEBOOK -> parseVocabularyEntryType(resolvedEntry == null ? null : resolvedEntry.getSourceEntryType());
        };
    }

    private Long resolveQuestionSourceEntryId(QuestionVo row, SessionVo session, EntryVo resolvedEntry) {
        return switch (QuizTargetType.valueOf(session.getTargetType())) {
            case PUBLIC_WORDBOOK -> row.getPublicEntryId();
            case USER_WORDBOOK -> row.getUserVocabularyEntryId();
            case WORD_NOTEBOOK -> resolvedEntry == null ? null : resolvedEntry.getSourceEntryId();
        };
    }

    private String normalizeWord(String word) {
        if (word == null) {
            return "";
        }
        return TextRepairUtils.repair(word).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private int commonPrefixLength(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int count = 0;
        while (count < max && left.charAt(count) == right.charAt(count)) {
            count++;
        }
        return count;
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }
            int[] nextPrevious = previous;
            previous = current;
            current = nextPrevious;
        }
        return previous[right.length()];
    }

    private int relatedScore(EntryVo current, EntryVo candidate) {
        int score = overlapScore(tokenize(current.getCategory()), tokenize(candidate.getCategory())) * 4;
        score += overlapScore(tokenize(current.getMeaningCn()), tokenize(candidate.getMeaningCn())) * 2;
        score += overlapScore(tokenize(current.getWord()), tokenize(candidate.getWord()));
        return score;
    }

    private int overlapScore(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String token : left) {
            if (right.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        if (value == null || value.isBlank()) {
            return tokens;
        }
        String normalized = UserFacingTextNormalizer.normalizeMeaningText(value).toLowerCase(Locale.ROOT);
        StringBuilder latin = new StringBuilder();
        StringBuilder han = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                flushLatinToken(tokens, latin);
                han.append(ch);
                continue;
            }
            flushHanTokens(tokens, han);
            if (Character.isLetterOrDigit(ch)) {
                latin.append(ch);
            } else {
                flushLatinToken(tokens, latin);
            }
        }
        flushLatinToken(tokens, latin);
        flushHanTokens(tokens, han);
        return tokens;
    }

    private void flushLatinToken(Set<String> tokens, StringBuilder token) {
        if (token.length() >= 2) {
            tokens.add(token.toString());
        }
        token.setLength(0);
    }

    private void flushHanTokens(Set<String> tokens, StringBuilder token) {
        if (token.length() == 1) {
            tokens.add(token.toString());
        } else if (token.length() > 1) {
            for (int index = 0; index < token.length() - 1; index++) {
                tokens.add(token.substring(index, index + 2));
            }
        }
        token.setLength(0);
    }

    private void addCandidates(LinkedHashMap<String, OptionPayload> values, List<String> candidates, OptionPayload correctOption) {
        for (String candidate : candidates) {
            if (values.size() >= 3) {
                return;
            }
            addCandidate(values, new OptionPayload(normalizeOptionValue(candidate, correctOption.value()), "", ""), correctOption);
        }
    }

    private void addPayloadCandidates(
            LinkedHashMap<String, OptionPayload> values,
            List<OptionPayload> candidates,
            OptionPayload correctOption
    ) {
        for (OptionPayload candidate : candidates) {
            if (values.size() >= 3) {
                return;
            }
            addCandidate(values, candidate, correctOption);
        }
    }

    private void addCandidate(
            LinkedHashMap<String, OptionPayload> values,
            OptionPayload candidate,
            OptionPayload correctOption
    ) {
        if (candidate == null || candidate.value() == null || candidate.value().isBlank()) {
            return;
        }
        String normalized = normalizeOptionValue(candidate.value(), correctOption.value());
        if (QuizTextUtools.hasHanCharacter(correctOption.value()) && !QuizTextUtools.hasHanCharacter(normalized)) {
            return;
        }
        if (normalized.equalsIgnoreCase(correctOption.value().trim())) {
            return;
        }
        values.putIfAbsent(normalized.toLowerCase(Locale.ROOT), candidate.withValue(normalized));
    }

    private EntryVo hydrateNotebookEntryFromSource(long userId, EntryVo notebookEntry) {
        EntryVo sourceEntry = resolveNotebookSourceEntry(userId, notebookEntry);
        if (sourceEntry == null) {
            return notebookEntry;
        }
        EntryVo hydrated = new EntryVo();
        hydrated.setId(notebookEntry.getId());
        hydrated.setWord(firstNonBlank(notebookEntry.getWord(), sourceEntry.getWord()));
        hydrated.setPhonetic(firstNonBlank(notebookEntry.getPhonetic(), sourceEntry.getPhonetic()));
        hydrated.setMeaningCn(firstNonBlank(notebookEntry.getMeaningCn(), sourceEntry.getMeaningCn()));
        hydrated.setCategory(firstNonBlank(notebookEntry.getCategory(), sourceEntry.getCategory()));
        hydrated.setAudioUrl(firstNonBlank(notebookEntry.getAudioUrl(), sourceEntry.getAudioUrl()));
        hydrated.setExampleSentence(firstNonBlank(notebookEntry.getExampleSentence(), sourceEntry.getExampleSentence()));
        hydrated.setCorrectedExampleSentence(firstNonBlank(notebookEntry.getCorrectedExampleSentence(), sourceEntry.getCorrectedExampleSentence()));
        hydrated.setChineseSentence(firstNonBlank(notebookEntry.getChineseSentence(), sourceEntry.getChineseSentence()));
        hydrated.setExampleAudioUrl(firstNonBlank(notebookEntry.getExampleAudioUrl(), sourceEntry.getExampleAudioUrl()));
        hydrated.setOptionA(notebookEntry.getOptionA());
        hydrated.setOptionAWord(notebookEntry.getOptionAWord());
        hydrated.setOptionAMeaningCn(notebookEntry.getOptionAMeaningCn());
        hydrated.setOptionB(notebookEntry.getOptionB());
        hydrated.setOptionBWord(notebookEntry.getOptionBWord());
        hydrated.setOptionBMeaningCn(notebookEntry.getOptionBMeaningCn());
        hydrated.setOptionC(notebookEntry.getOptionC());
        hydrated.setOptionCWord(notebookEntry.getOptionCWord());
        hydrated.setOptionCMeaningCn(notebookEntry.getOptionCMeaningCn());
        hydrated.setOptionD(notebookEntry.getOptionD());
        hydrated.setOptionDWord(notebookEntry.getOptionDWord());
        hydrated.setOptionDMeaningCn(notebookEntry.getOptionDMeaningCn());
        hydrated.setCorrectOption(notebookEntry.getCorrectOption());
        hydrated.setSourceEntryType(firstNonBlank(notebookEntry.getSourceEntryType(), sourceEntry.getSourceEntryType()));
        hydrated.setSourceEntryId(notebookEntry.getSourceEntryId() != null ? notebookEntry.getSourceEntryId() : sourceEntry.getSourceEntryId());
        return hydrated;
    }

    private EntryVo resolveNotebookSourceEntry(long userId, EntryVo notebookEntry) {
        VocabularyEntryType sourceType = parseVocabularyEntryType(notebookEntry.getSourceEntryType());
        Long sourceEntryId = notebookEntry.getSourceEntryId();
        if (sourceType == null || sourceEntryId == null) {
            return null;
        }

        EntryVo sourceEntry = switch (sourceType) {
            case PUBLIC -> vocabularyEntryMapper.loadPublicVocabularyEntryById(sourceEntryId);
            case USER -> vocabularyEntryMapper.loadUserEntryById(userId, sourceEntryId);
        };
        if (sourceEntry == null) {
            return null;
        }
        sourceEntry.setSourceEntryType(sourceType.name());
        sourceEntry.setSourceEntryId(sourceEntryId);
        return sourceEntry;
    }

    private VocabularyEntryType parseVocabularyEntryType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return VocabularyEntryType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private void applySavedNotebookOptions(WordNotebookEntryPo row, AddWordNotebookEntryRequest request) {
        List<QuizOptionDto> optionDetails = request.optionDetails();
        if (optionDetails == null || optionDetails.size() != 4) {
            row.setOptionA("");
            row.setOptionAWord("");
            row.setOptionAMeaningCn("");
            row.setOptionB("");
            row.setOptionBWord("");
            row.setOptionBMeaningCn("");
            row.setOptionC("");
            row.setOptionCWord("");
            row.setOptionCMeaningCn("");
            row.setOptionD("");
            row.setOptionDWord("");
            row.setOptionDMeaningCn("");
            row.setCorrectOption("");
            return;
        }

        QuizOptionDto optionA = optionDetails.get(0);
        QuizOptionDto optionB = optionDetails.get(1);
        QuizOptionDto optionC = optionDetails.get(2);
        QuizOptionDto optionD = optionDetails.get(3);
        row.setOptionA(normalizeSavedNotebookOptionValue(optionA == null ? null : optionA.value()));
        row.setOptionAWord(normalizeSavedNotebookOptionWord(optionA == null ? null : optionA.word()));
        row.setOptionAMeaningCn(normalizeSavedNotebookOptionMeaning(optionA == null ? null : optionA.meaningCn(), row.getOptionA()));
        row.setOptionB(normalizeSavedNotebookOptionValue(optionB == null ? null : optionB.value()));
        row.setOptionBWord(normalizeSavedNotebookOptionWord(optionB == null ? null : optionB.word()));
        row.setOptionBMeaningCn(normalizeSavedNotebookOptionMeaning(optionB == null ? null : optionB.meaningCn(), row.getOptionB()));
        row.setOptionC(normalizeSavedNotebookOptionValue(optionC == null ? null : optionC.value()));
        row.setOptionCWord(normalizeSavedNotebookOptionWord(optionC == null ? null : optionC.word()));
        row.setOptionCMeaningCn(normalizeSavedNotebookOptionMeaning(optionC == null ? null : optionC.meaningCn(), row.getOptionC()));
        row.setOptionD(normalizeSavedNotebookOptionValue(optionD == null ? null : optionD.value()));
        row.setOptionDWord(normalizeSavedNotebookOptionWord(optionD == null ? null : optionD.word()));
        row.setOptionDMeaningCn(normalizeSavedNotebookOptionMeaning(optionD == null ? null : optionD.meaningCn(), row.getOptionD()));
        row.setCorrectOption(normalizeSavedNotebookOptionValue(request.correctOption()));
    }

    private String normalizeSavedNotebookOptionValue(String value) {
        return UserFacingTextNormalizer.normalizeMeaningText(valueOrEmpty(value)).trim();
    }

    private String normalizeSavedNotebookOptionWord(String value) {
        return TextRepairUtils.repair(valueOrEmpty(value)).trim();
    }

    private String normalizeSavedNotebookOptionMeaning(String meaning, String fallbackValue) {
        String normalized = UserFacingTextNormalizer.normalizeMeaningText(valueOrEmpty(meaning)).trim();
        return normalized.isBlank() ? UserFacingTextNormalizer.normalizeMeaningText(valueOrEmpty(fallbackValue)).trim() : normalized;
    }

    private String normalizeOptionValue(String candidate, String correctOption) {
        return QuizTextUtools.hasHanCharacter(correctOption)
                ? UserFacingTextNormalizer.normalizeMeaningText(candidate).trim()
                : TextRepairUtils.repair(candidate).trim();
    }

    private String toClientAudioUrl(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            return "";
        }
        String normalized = audioUrl.trim();
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        }
        return "/search/audio-proxy?src=" + URLEncoder.encode(normalized, StandardCharsets.UTF_8);
    }

    private String normalizeOptionalUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("//")) {
            return "https:" + normalized;
        }
        return normalized;
    }

    private String normalizeNotebookName(String value) {
        String normalized = UserFacingTextNormalizer.normalizeDisplayText(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Word notebook name cannot be empty");
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String normalizeNotebookWord(String value) {
        if (value == null) {
            return "";
        }
        String repaired = TextRepairUtils.repair(value).trim();
        String stripped = repaired.replaceAll("^[^A-Za-z]+|[^A-Za-z]+$", "");
        String normalized = (stripped.isBlank() ? repaired : stripped).toLowerCase(Locale.ROOT);
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String toClientExampleAudioUrl(Long publicEntryId, String exampleAudioUrl) {
        if (publicEntryId == null) {
            return normalizeOptionalUrl(exampleAudioUrl);
        }
        return normalizeOptionalUrl(exampleAudioUrl).isBlank()
                ? ""
                : "/search/example-audio/" + publicEntryId;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String resolveCurrentWord(QuestionVo row) {
        if (row.getCurrentWord() != null && !row.getCurrentWord().isBlank()) {
            return TextRepairUtils.repair(row.getCurrentWord());
        }
        if (PromptType.CN_TO_EN.name().equals(row.getPromptType())) {
            return TextRepairUtils.repair(row.getOptionA());
        }
        return TextRepairUtils.repair(row.getPromptText());
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

    private void requireWordNotebook(long userId, long notebookId) {
        if (wordNotebookMapper.countOwnedNotebook(userId, notebookId) == 0) {
            throw new ForbiddenException("You cannot access this word notebook");
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
        TodayAnswerStatsVo todayStats = sessionMapper.loadTodayAnswerStats(
                row.getUserId(),
                row.getTargetType(),
                row.getTargetId()
        );
        return new QuizSessionDto(
                row.getId(),
                row.getTargetId(),
                targetType,
                row.getTargetId(),
                QuizMode.valueOf(row.getMode()),
                row.getTotalQuestions(),
                row.getAnsweredQuestions(),
                row.getCorrectAnswers(),
                todayStats == null ? 0 : todayStats.getCorrectAttempts(),
                todayStats == null ? 0 : todayStats.getTotalAttempts(),
                resolveQuizOptionStrategy(row.getUserId(), targetType),
                row.getStatus()
        );
    }

    private PublicWordbookProgressSnapshotDto mapPublicWordbookProgress(PublicWordbookSubscriptionVo row) {
        return new PublicWordbookProgressSnapshotDto(
                row.getPublicWordbookId(),
                row.getCompletedCount(),
                row.getDailyTargetCount(),
                row.getTodayCompletedCount(),
                row.getWordCount()
        );
    }

    private QuizOptionDto toPersistedOptionDetail(String value, String word, String meaningCn) {
        return new QuizOptionDto(
                value,
                word == null ? "" : TextRepairUtils.repair(word).trim(),
                meaningCn == null || meaningCn.isBlank()
                        ? UserFacingTextNormalizer.normalizeMeaningText(value).trim()
                        : UserFacingTextNormalizer.normalizeMeaningText(meaningCn).trim()
        );
    }

    private record AttemptPayload(
            String promptText,
            List<OptionPayload> options,
            String correctOption
    ) {
    }

    private record OptionPayload(
            String value,
            String word,
            String meaningCn
    ) {
        private OptionPayload withValue(String nextValue) {
            return new OptionPayload(nextValue, word, meaningCn);
        }
    }

    private record SessionSeed(
            int startOffset,
            int totalQuestions
    ) {
    }
}
