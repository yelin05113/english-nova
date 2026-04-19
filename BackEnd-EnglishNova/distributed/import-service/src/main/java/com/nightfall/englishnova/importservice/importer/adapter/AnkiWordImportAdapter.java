package com.nightfall.englishnova.importservice.importer.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.importservice.importer.ImportedVocabularyRecord;
import com.nightfall.englishnova.importservice.importer.WordImportAdapter;
import com.nightfall.englishnova.shared.dto.ImportPresetDto;
import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Anki 单词导入适配器，支持解析 .apkg 卡包文件并提取牌组中的单词、释义与例句。
 * <p>核心流程：解压 APKG → 读取 SQLite collection 数据库 → 解析 notes 字段 → 按 word|meaning 去重。
 */
@Component
public class AnkiWordImportAdapter implements WordImportAdapter {

    private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SOUND_TAG = Pattern.compile("\\[sound:[^\\]]+]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 返回适配器对应的导入平台。
     *
     * @return {@link WordImportPlatform#ANKI}
     */
    @Override
    public WordImportPlatform platform() {
        return WordImportPlatform.ANKI;
    }

    /**
     * 返回 Anki 导入预设信息。
     *
     * @return 导入预设 DTO
     */
    @Override
    public ImportPresetDto preset() {
        return new ImportPresetDto(
                platform(),
                "Anki 卡包导入",
                "解析 APKG 牌组，抽取单词和释义后直接写入词库。",
                List.of("apkg"),
                List.of("word", "meaning", "example", "deckName", "tags")
        );
    }

    /**
     * 判断是否支持 .apkg 后缀的文件。
     *
     * @param fileName 文件名
     * @return 若文件名为 .apkg 结尾则返回 true
     */
    @Override
    public boolean supportsFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".apkg");
    }

    /**
     * 解析 Anki 卡包文件并返回标准化的词汇记录列表。
     *
     * @param filePath 卡包文件路径
     * @return 词汇记录列表
     * @throws IOException 当文件读取或解析失败时
     */
    @Override
    public List<ImportedVocabularyRecord> importEntries(Path filePath) throws IOException {
        Path databasePath = extractCollectionDatabase(filePath);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            Map<Long, String> deckNames = readDeckNames(connection);
            return readNotes(connection, deckNames);
        } catch (SQLException exception) {
            throw new IllegalArgumentException("无法解析 Anki 卡包内容", exception);
        } finally {
            Files.deleteIfExists(databasePath);
        }
    }

    /**
     * 从 APKG 压缩包中提取 collection SQLite 数据库到临时文件。
     * 优先查找 collection.anki21，若不存在则回退到 collection.anki2。
     *
     * @param filePath APKG 文件路径
     * @return 提取出的 SQLite 临时文件路径
     * @throws IOException 当文件读取失败或找不到 collection 数据库时
     */
    private Path extractCollectionDatabase(Path filePath) throws IOException {
        Path extracted = Files.createTempFile("english-nova-anki-", ".anki2");
        try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
            ZipEntry entry = zipFile.getEntry("collection.anki21");
            if (entry == null) {
                entry = zipFile.getEntry("collection.anki2");
            }
            if (entry == null) {
                throw new IllegalArgumentException("Anki 卡包里没有 collection.anki2 或 collection.anki21");
            }

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                Files.copy(inputStream, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return extracted;
    }

    /**
     * 读取 Anki 数据库中的牌组（deck）名称映射。
     * <p>从 col 表的 decks JSON 字段解析，键为 deck ID，值为 deck 名称。
     *
     * @param connection SQLite 连接
     * @return deck ID 到 deck 名称的映射
     * @throws SQLException 当 SQL 执行失败时
     * @throws IOException  当 JSON 解析失败时
     */
    private Map<Long, String> readDeckNames(Connection connection) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT decks FROM col LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Map.of();
            }

            String decksJson = resultSet.getString("decks");
            if (decksJson == null || decksJson.isBlank()) {
                return Map.of();
            }

            Map<String, Map<String, Object>> parsed = objectMapper.readValue(
                    decksJson,
                    new TypeReference<>() {
                    }
            );

            Map<Long, String> deckNames = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : parsed.entrySet()) {
                Object name = entry.getValue().get("name");
                try {
                    deckNames.put(Long.parseLong(entry.getKey()), name == null ? "Anki 导入" : name.toString());
                } catch (NumberFormatException ignored) {
                    // Ignore non-numeric deck ids from malformed packages.
                }
            }
            return deckNames;
        }
    }

    /**
     * 读取 notes 与 cards 表，将每个 note 的字段拆分为单词记录，并按 word|meaning 去重。
     * <p>算法：以 note.id 分组，取最小 did 作为所属 deck；flds 按 \u001f 分割后映射为 word、meaning 等字段。
     *
     * @param connection SQLite 连接
     * @param deckNames  牌组名称映射
     * @return 去重后的词汇记录列表
     * @throws SQLException 当 SQL 执行失败时
     */
    private List<ImportedVocabularyRecord> readNotes(Connection connection, Map<Long, String> deckNames) throws SQLException {
        String sql = """
                SELECT n.flds, n.tags, MIN(c.did) AS did
                FROM notes n
                LEFT JOIN cards c ON c.nid = n.id
                GROUP BY n.id, n.flds, n.tags
                ORDER BY n.id
                """;

        LinkedHashMap<String, ImportedVocabularyRecord> records = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String[] fields = resultSet.getString("flds").split("\u001f", -1);
                ImportedVocabularyRecord record = mapFields(
                        fields,
                        deckNames.getOrDefault(resultSet.getLong("did"), "Anki 导入"),
                        resultSet.getString("tags")
                );
                if (record == null) {
                    continue;
                }

                String key = record.word().toLowerCase(Locale.ROOT) + "|" + record.meaning();
                records.putIfAbsent(key, record);
            }
        }
        return List.copyOf(records.values());
    }

    /**
     * 将 Anki 字段数组映射为 {@link ImportedVocabularyRecord}。
     * <p>启发式规则：
     * <ul>
     *   <li>若第一个字段含中文且第二个字段含拉丁字母，则交换两者（保证 word 在前）。</li>
     *   <li>优先从字段中提取 /.../ 格式的音标。</li>
     *   <li>分类优先使用 deckName，其次使用 tags，最后回退为 "Anki 导入"。</li>
     * </ul>
     *
     * @param fields   字段数组
     * @param deckName 所属牌组名称
     * @param tags     标签字符串
     * @return 词汇记录，若无法提取有效单词则返回 null
     */
    private ImportedVocabularyRecord mapFields(String[] fields, String deckName, String tags) {
        if (fields.length == 0) {
            return null;
        }

        String first = cleanField(fields[0]);
        String second = fields.length > 1 ? cleanField(fields[1]) : "";
        String third = fields.length > 2 ? cleanField(fields[2]) : "";

        if (containsChinese(first) && containsLatin(second)) {
            String swapped = first;
            first = second;
            second = swapped;
        }

        String word = first;
        if (word.isBlank()) {
            return null;
        }

        String phonetic = extractPhonetic(fields);
        String meaning = !second.isBlank() ? second : (!third.isBlank() ? third : "Anki 导入释义");
        String example = !third.isBlank() ? third : meaning;
        String category = cleanField(deckName);
        if (category.isBlank()) {
            category = cleanTags(tags);
        }
        if (category.isBlank()) {
            category = "Anki 导入";
        }

        return new ImportedVocabularyRecord(word, phonetic, meaning, example, category, scoreDifficulty(word));
    }

    /**
     * 从字段数组中提取以斜杠包裹的音标文本（如 /kæt/）。
     *
     * @param fields 字段数组
     * @return 提取到的音标，若未找到则返回 "-"
     */
    private String extractPhonetic(String[] fields) {
        for (String field : fields) {
            String cleaned = cleanField(field);
            if (cleaned.startsWith("/") && cleaned.endsWith("/") && cleaned.length() > 2) {
                return cleaned;
            }
        }
        return "-";
    }

    /**
     * 将标签字符串中的多余空白压缩为单个空格。
     *
     * @param tags 原始标签字符串
     * @return 清理后的标签字符串
     */
    private String cleanTags(String tags) {
        if (tags == null) {
            return "";
        }
        return MULTI_SPACE.matcher(tags.trim()).replaceAll(" ");
    }

    /**
     * 清理单个字段：移除 sound 标签、HTML 标签与换行符，进行 HTML 反转义，并压缩空白。
     *
     * @param value 原始字段值
     * @return 清理后的纯文本
     */
    private String cleanField(String value) {
        if (value == null) {
            return "";
        }
        String text = SOUND_TAG.matcher(value).replaceAll(" ");
        text = HTML_BREAK.matcher(text).replaceAll(" ");
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = HtmlUtils.htmlUnescape(text);
        text = text.replace('\u001f', ' ').replace('\u0000', ' ');
        return MULTI_SPACE.matcher(text).replaceAll(" ").trim();
    }

    /**
     * 判断文本是否包含中文字符（CJK HAN）。
     *
     * @param value 待检测文本
     * @return 若包含中文则返回 true
     */
    private boolean containsChinese(String value) {
        return CJK.matcher(value).find();
    }

    /**
     * 判断文本是否包含拉丁字母。
     *
     * @param value 待检测文本
     * @return 若包含拉丁字母则返回 true
     */
    private boolean containsLatin(String value) {
        return LATIN.matcher(value).find();
    }

    /**
     * 根据单词长度计算难度等级（1-5）。
     * <p>规则：长度 ≥10 为 5，≥8 为 4，≥6 为 3，≥4 为 2，其余为 1。
     *
     * @param word 单词
     * @return 难度等级
     */
    private int scoreDifficulty(String word) {
        int length = word == null ? 0 : word.trim().length();
        if (length >= 10) {
            return 5;
        }
        if (length >= 8) {
            return 4;
        }
        if (length >= 6) {
            return 3;
        }
        if (length >= 4) {
            return 2;
        }
        return 1;
    }
}
