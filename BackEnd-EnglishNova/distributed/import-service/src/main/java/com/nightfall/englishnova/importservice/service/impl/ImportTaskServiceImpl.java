package com.nightfall.englishnova.importservice.service.impl;

import com.nightfall.englishnova.importservice.config.EnglishNovaProperties;
import com.nightfall.englishnova.importservice.importer.ImportedVocabularyRecord;
import com.nightfall.englishnova.importservice.importer.WordImportAdapter;
import com.nightfall.englishnova.importservice.importer.WordImportDispatcher;
import com.nightfall.englishnova.importservice.mapper.ImportTaskMapper;
import com.nightfall.englishnova.importservice.mapper.UserWordProgressMapper;
import com.nightfall.englishnova.importservice.mapper.VocabularyEntryMapper;
import com.nightfall.englishnova.importservice.mapper.WordbookMapper;
import com.nightfall.englishnova.importservice.domain.po.VocabularyEntryPo;
import com.nightfall.englishnova.importservice.domain.po.WordbookPo;
import com.nightfall.englishnova.importservice.domain.vo.ImportTaskVo;
import com.nightfall.englishnova.importservice.service.ImportTaskService;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.ImportPresetDto;
import com.nightfall.englishnova.shared.dto.ImportTaskDto;
import com.nightfall.englishnova.shared.dto.ImportTaskRequest;
import com.nightfall.englishnova.shared.enums.ProgressStatus;
import com.nightfall.englishnova.shared.enums.VocabularyVisibility;
import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import com.nightfall.englishnova.shared.events.WordbookImportedEvent;
import com.nightfall.englishnova.shared.exception.NotFoundException;
import com.nightfall.englishnova.shared.exception.UnauthorizedException;
import com.nightfall.englishnova.shared.text.TextRepairUtils;
import com.nightfall.englishnova.shared.text.UserFacingTextNormalizer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.nightfall.englishnova.importservice.utools.ImportTaskUtools.defaultText;
import static com.nightfall.englishnova.importservice.utools.ImportTaskUtools.resolveImportSource;
import static com.nightfall.englishnova.importservice.utools.ImportTaskUtools.resolveSourceName;
import static com.nightfall.englishnova.importservice.utools.ImportTaskUtools.safeFileName;
import static com.nightfall.englishnova.importservice.utools.ImportTaskUtools.truncate;

@Service
public class ImportTaskServiceImpl implements ImportTaskService {

    private final WordImportDispatcher dispatcher;
    private final EnglishNovaProperties properties;
    private final ImportTaskMapper importTaskMapper;
    private final WordbookMapper wordbookMapper;
    private final VocabularyEntryMapper vocabularyEntryMapper;
    private final UserWordProgressMapper userWordProgressMapper;
    private final RabbitTemplate rabbitTemplate;

    public ImportTaskServiceImpl(
            WordImportDispatcher dispatcher,
            EnglishNovaProperties properties,
            ImportTaskMapper importTaskMapper,
            WordbookMapper wordbookMapper,
            VocabularyEntryMapper vocabularyEntryMapper,
            UserWordProgressMapper userWordProgressMapper,
            RabbitTemplate rabbitTemplate
    ) {
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.importTaskMapper = importTaskMapper;
        this.wordbookMapper = wordbookMapper;
        this.vocabularyEntryMapper = vocabularyEntryMapper;
        this.userWordProgressMapper = userWordProgressMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public List<ImportPresetDto> listPresets() {
        return dispatcher.getAdapters().stream()
                .map(WordImportAdapter::preset)
                .sorted(Comparator.comparing(ImportPresetDto::title))
                .toList();
    }

    @Override
    public List<ImportTaskDto> listTasks(CurrentUser user) {
        return importTaskMapper.listByUser(user.id()).stream()
                .map(this::mapTask)
                .toList();
    }

    @Override
    @Transactional
    public ImportTaskDto createTask(CurrentUser user, ImportTaskRequest request) {
        requireAdapter(request.platform());
        ImportTaskDto task = persistTask(
                user.id(),
                null,
                request.platform(),
                request.sourceName(),
                request.estimatedCards(),
                0,
                "QUEUED",
                properties.getQueue(),
                null
        );
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), task.taskId());
        return task;
    }

    @Override
    @Transactional
    public ImportTaskDto importFile(CurrentUser user, WordImportPlatform platform, String sourceName, MultipartFile file) {
        if (user == null) {
            throw new UnauthorizedException("请先登录");
        }
        WordImportAdapter adapter = requireAdapter(platform);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        if (!adapter.supportsFile(originalFileName)) {
            throw new IllegalArgumentException("当前平台不支持该文件格式");
        }

        String resolvedSourceName = resolveSourceName(sourceName, originalFileName);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("english-nova-import-", "-" + safeFileName(originalFileName));
            file.transferTo(tempFile);

            List<ImportedVocabularyRecord> records = adapter.importEntries(tempFile);
            if (records.isEmpty()) {
                throw new IllegalArgumentException("没有从文件中解析出可导入的单词");
            }

            long wordbookId = createWordbook(user.id(), resolvedSourceName, platform, resolvedSourceName, records.size());
            batchInsertVocabulary(user.id(), wordbookId, platform, records);
            initializeProgress(user.id(), wordbookId);
            wordbookMapper.syncWordbookCount(wordbookId);

            ImportTaskDto task = persistTask(
                    user.id(),
                    wordbookId,
                    platform,
                    resolvedSourceName,
                    records.size(),
                    records.size(),
                    "IMPORTED",
                    "direct-import",
                    OffsetDateTime.now(ZoneOffset.UTC)
            );

            rabbitTemplate.convertAndSend(
                    properties.getExchange(),
                    properties.getIndexedRoutingKey(),
                    new WordbookImportedEvent(user.id(), wordbookId)
            );
            return task;
        } catch (IOException exception) {
            throw new IllegalArgumentException("保存导入文件失败", exception);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best effort cleanup.
                }
            }
        }
    }

    private WordImportAdapter requireAdapter(WordImportPlatform platform) {
        WordImportAdapter adapter = dispatcher.getAdapter(platform);
        if (adapter == null) {
            throw new NotFoundException("不支持的导入平台");
        }
        return adapter;
    }

    private long createWordbook(long userId, String name, WordImportPlatform platform, String sourceName, int wordCount) {
        WordbookPo wordbook = new WordbookPo();
        wordbook.setUserId(userId);
        wordbook.setName(truncate(UserFacingTextNormalizer.normalizeDisplayText(name), 120));
        wordbook.setPlatform(platform.name());
        wordbook.setSourceName(truncate(UserFacingTextNormalizer.normalizeDisplayText(sourceName), 120));
        wordbook.setImportSource(resolveImportSource(platform));
        wordbook.setWordCount(wordCount);
        wordbookMapper.insert(wordbook);
        if (wordbook.getId() == null) {
            throw new IllegalArgumentException("创建词书失败");
        }
        return wordbook.getId();
    }

    private void batchInsertVocabulary(
            long userId,
            long wordbookId,
            WordImportPlatform platform,
            List<ImportedVocabularyRecord> records
    ) {
        String importSource = resolveImportSource(platform);
        List<VocabularyEntryPo> rows = records.stream()
                .map(record -> toVocabularyRow(userId, wordbookId, record, importSource))
                .toList();
        vocabularyEntryMapper.batchInsert(rows);
    }

    private VocabularyEntryPo toVocabularyRow(
            long userId,
            long wordbookId,
            ImportedVocabularyRecord record,
            String importSource
    ) {
        VocabularyEntryPo row = new VocabularyEntryPo();
        row.setUserId(userId);
        row.setWordbookId(wordbookId);
        row.setWord(truncate(TextRepairUtils.repair(record.word()), 120));
        row.setPhonetic(truncate(TextRepairUtils.repair(defaultText(record.phonetic(), "-")), 120));
        row.setMeaningCn(truncate(UserFacingTextNormalizer.normalizeMeaningText(defaultText(record.meaning(), "导入释义")), 255));
        row.setExampleSentence(truncate(UserFacingTextNormalizer.normalizeDisplayText(defaultText(record.exampleSentence(), record.meaning())), 255));
        row.setCategory(truncate(UserFacingTextNormalizer.normalizeMeaningText(defaultText(record.category(), "Anki 导入")), 120));
        row.setDifficulty(Math.max(1, Math.min(5, record.difficulty())));
        row.setVisibility(VocabularyVisibility.PRIVATE.name());
        row.setAudioUrl("");
        row.setImportSource(importSource);
        return row;
    }

    private void initializeProgress(long userId, long wordbookId) {
        List<Long> entryIds = vocabularyEntryMapper.findIdsByUserAndWordbook(userId, wordbookId);
        if (!entryIds.isEmpty()) {
            userWordProgressMapper.batchInsertNewProgress(userId, entryIds, ProgressStatus.NEW.name());
        }
    }

    private ImportTaskDto persistTask(
            long userId,
            Long wordbookId,
            WordImportPlatform platform,
            String sourceName,
            int estimatedCards,
            int importedCards,
            String status,
            String queueName,
            OffsetDateTime finishedAt
    ) {
        String normalizedSourceName = truncate(UserFacingTextNormalizer.normalizeDisplayText(sourceName), 120);
        String taskId = UUID.randomUUID().toString();
        OffsetDateTime queuedAt = OffsetDateTime.now(ZoneOffset.UTC);
        ImportTaskDto task = new ImportTaskDto(
                taskId,
                wordbookId,
                platform,
                normalizedSourceName,
                estimatedCards,
                importedCards,
                status,
                queuedAt,
                finishedAt,
                queueName
        );

        importTaskMapper.insertTask(
                task.taskId(),
                userId,
                task.wordbookId(),
                task.platform().name(),
                task.sourceName(),
                task.estimatedCards(),
                task.importedCards(),
                task.status(),
                Timestamp.from(task.queuedAt().toInstant()),
                task.finishedAt() == null ? null : Timestamp.from(task.finishedAt().toInstant()),
                task.queueName()
        );
        return task;
    }

    private ImportTaskDto mapTask(ImportTaskVo row) {
        return new ImportTaskDto(
                row.getTaskId(),
                row.getWordbookId(),
                WordImportPlatform.valueOf(row.getPlatform()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getSourceName()),
                row.getEstimatedCards(),
                row.getImportedCards(),
                row.getStatus(),
                row.getQueuedAt().toInstant().atOffset(ZoneOffset.UTC),
                row.getFinishedAt() == null ? null : row.getFinishedAt().toInstant().atOffset(ZoneOffset.UTC),
                row.getQueueName()
        );
    }

}
