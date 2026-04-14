package com.heima.englishnova.importservice.service;

import com.heima.englishnova.importservice.config.EnglishNovaProperties;
import com.heima.englishnova.importservice.importer.ImportedVocabularyRecord;
import com.heima.englishnova.importservice.importer.WordImportAdapter;
import com.heima.englishnova.importservice.importer.WordImportDispatcher;
import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.dto.ImportTaskDto;
import com.heima.englishnova.shared.dto.ImportTaskRequest;
import com.heima.englishnova.shared.enums.ProgressStatus;
import com.heima.englishnova.shared.enums.VocabularyVisibility;
import com.heima.englishnova.shared.events.WordbookImportedEvent;
import com.heima.englishnova.shared.exception.NotFoundException;
import com.heima.englishnova.shared.exception.UnauthorizedException;
import com.heima.englishnova.shared.text.TextRepairUtils;
import com.heima.englishnova.shared.text.UserFacingTextNormalizer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImportTaskService {

    private final WordImportDispatcher dispatcher;
    private final EnglishNovaProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    public ImportTaskService(
            WordImportDispatcher dispatcher,
            EnglishNovaProperties properties,
            JdbcTemplate jdbcTemplate,
            RabbitTemplate rabbitTemplate
    ) {
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    public List<ImportPresetDto> listPresets() {
        return dispatcher.getAdapters().stream()
                .map(WordImportAdapter::preset)
                .sorted(Comparator.comparing(ImportPresetDto::title))
                .toList();
    }

    public List<ImportTaskDto> listTasks(CurrentUser user) {
        return jdbcTemplate.query(
                """
                SELECT task_id, wordbook_id, platform, source_name, estimated_cards, imported_cards, status, queued_at, finished_at, queue_name
                FROM import_tasks
                WHERE user_id = ?
                ORDER BY queued_at DESC
                """,
                (resultSet, rowNum) -> new ImportTaskDto(
                        resultSet.getString("task_id"),
                        getLong(resultSet, "wordbook_id"),
                        com.heima.englishnova.shared.enums.WordImportPlatform.valueOf(resultSet.getString("platform")),
                        UserFacingTextNormalizer.normalizeDisplayText(resultSet.getString("source_name")),
                        resultSet.getInt("estimated_cards"),
                        resultSet.getInt("imported_cards"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("queued_at").toInstant().atOffset(ZoneOffset.UTC),
                        resultSet.getTimestamp("finished_at") == null ? null : resultSet.getTimestamp("finished_at").toInstant().atOffset(ZoneOffset.UTC),
                        resultSet.getString("queue_name")
                ),
                user.id()
        );
    }

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

    @Transactional
    public ImportTaskDto importFile(CurrentUser user, com.heima.englishnova.shared.enums.WordImportPlatform platform, String sourceName, MultipartFile file) {
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
            syncWordbookCount(wordbookId);

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

    private WordImportAdapter requireAdapter(com.heima.englishnova.shared.enums.WordImportPlatform platform) {
        WordImportAdapter adapter = dispatcher.getAdapter(platform);
        if (adapter == null) {
            throw new NotFoundException("不支持的导入平台");
        }
        return adapter;
    }

    private long createWordbook(long userId, String name, com.heima.englishnova.shared.enums.WordImportPlatform platform, String sourceName, int wordCount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO wordbooks(user_id, name, platform, source_name, import_source, word_count, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, userId);
            statement.setString(2, truncate(UserFacingTextNormalizer.normalizeDisplayText(name), 120));
            statement.setString(3, platform.name());
            statement.setString(4, truncate(UserFacingTextNormalizer.normalizeDisplayText(sourceName), 120));
            statement.setString(5, resolveImportSource(platform));
            statement.setInt(6, wordCount);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalArgumentException("创建词书失败");
        }
        return key.longValue();
    }

    private void batchInsertVocabulary(
            long userId,
            long wordbookId,
            com.heima.englishnova.shared.enums.WordImportPlatform platform,
            List<ImportedVocabularyRecord> records
    ) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO vocabulary_entries(
                    user_id, wordbook_id, word, phonetic, meaning_cn, example_sentence, category,
                    difficulty, visibility, audio_url, import_source, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                records,
                records.size(),
                (preparedStatement, record) -> {
                    preparedStatement.setLong(1, userId);
                    preparedStatement.setLong(2, wordbookId);
                    preparedStatement.setString(3, truncate(TextRepairUtils.repair(record.word()), 120));
                    preparedStatement.setString(4, truncate(TextRepairUtils.repair(defaultText(record.phonetic(), "-")), 120));
                    preparedStatement.setString(5, truncate(UserFacingTextNormalizer.normalizeMeaningText(defaultText(record.meaning(), "导入释义")), 255));
                    preparedStatement.setString(6, truncate(UserFacingTextNormalizer.normalizeDisplayText(defaultText(record.exampleSentence(), record.meaning())), 255));
                    preparedStatement.setString(7, truncate(UserFacingTextNormalizer.normalizeMeaningText(defaultText(record.category(), "Anki 导入")), 120));
                    preparedStatement.setInt(8, Math.max(1, Math.min(5, record.difficulty())));
                    preparedStatement.setString(9, VocabularyVisibility.PRIVATE.name());
                    preparedStatement.setString(10, "");
                    preparedStatement.setString(11, resolveImportSource(platform));
                }
        );
    }

    private void initializeProgress(long userId, long wordbookId) {
        List<Long> entryIds = jdbcTemplate.query(
                "SELECT id FROM vocabulary_entries WHERE user_id = ? AND wordbook_id = ?",
                (resultSet, rowNum) -> resultSet.getLong("id"),
                userId,
                wordbookId
        );
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO user_word_progress(user_id, vocabulary_entry_id, status, correct_count, wrong_count)
                VALUES (?, ?, ?, 0, 0)
                """,
                entryIds,
                entryIds.size(),
                (preparedStatement, entryId) -> {
                    preparedStatement.setLong(1, userId);
                    preparedStatement.setLong(2, entryId);
                    preparedStatement.setString(3, ProgressStatus.NEW.name());
                }
        );
    }

    private void syncWordbookCount(long wordbookId) {
        jdbcTemplate.update(
                """
                UPDATE wordbooks
                SET word_count = (
                    SELECT COUNT(*)
                    FROM vocabulary_entries
                    WHERE wordbook_id = ?
                )
                WHERE id = ?
                """,
                wordbookId,
                wordbookId
        );
    }

    private ImportTaskDto persistTask(
            long userId,
            Long wordbookId,
            com.heima.englishnova.shared.enums.WordImportPlatform platform,
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

        jdbcTemplate.update(
                """
                INSERT INTO import_tasks(task_id, user_id, wordbook_id, platform, source_name, estimated_cards, imported_cards, status, queued_at, finished_at, queue_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
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

    private String resolveSourceName(String sourceName, String originalFileName) {
        if (sourceName != null && !sourceName.isBlank()) {
            return truncate(UserFacingTextNormalizer.normalizeDisplayText(sourceName), 120);
        }
        if (originalFileName != null && !originalFileName.isBlank()) {
            String normalized = originalFileName.trim();
            int dotIndex = normalized.lastIndexOf('.');
            if (dotIndex > 0) {
                normalized = normalized.substring(0, dotIndex);
            }
            return truncate(UserFacingTextNormalizer.normalizeDisplayText(normalized), 120);
        }
        return "anki-import";
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "upload.bin";
        }
        return originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return TextRepairUtils.repair(fallback);
        }
        return TextRepairUtils.repair(value);
    }

    private String resolveImportSource(com.heima.englishnova.shared.enums.WordImportPlatform platform) {
        return platform.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Long getLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
