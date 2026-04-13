package com.heima.englishnova.importservice.importer.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.englishnova.importservice.importer.ImportedVocabularyRecord;
import com.heima.englishnova.importservice.importer.WordImportAdapter;
import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.enums.WordImportPlatform;
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

@Component
public class AnkiWordImportAdapter implements WordImportAdapter {

    private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SOUND_TAG = Pattern.compile("\\[sound:[^\\]]+]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WordImportPlatform platform() {
        return WordImportPlatform.ANKI;
    }

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

    @Override
    public boolean supportsFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".apkg");
    }

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

    private String extractPhonetic(String[] fields) {
        for (String field : fields) {
            String cleaned = cleanField(field);
            if (cleaned.startsWith("/") && cleaned.endsWith("/") && cleaned.length() > 2) {
                return cleaned;
            }
        }
        return "-";
    }

    private String cleanTags(String tags) {
        if (tags == null) {
            return "";
        }
        return MULTI_SPACE.matcher(tags.trim()).replaceAll(" ");
    }

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

    private boolean containsChinese(String value) {
        return CJK.matcher(value).find();
    }

    private boolean containsLatin(String value) {
        return LATIN.matcher(value).find();
    }

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
