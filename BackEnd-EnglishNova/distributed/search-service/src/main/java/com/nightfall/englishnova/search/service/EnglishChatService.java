package com.nightfall.englishnova.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.search.config.ExampleEnrichmentProperties;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.EnglishChatMessageDto;
import com.nightfall.englishnova.shared.dto.EnglishChatRequestDto;
import com.nightfall.englishnova.shared.dto.EnglishQuestionContextDto;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EnglishChatService {

    private static final String SYSTEM_PROMPT = """
            你是 EnglishNova 项目的英语学习 AI 助手。

            任务：
            1. 解答英语语法问题。
            2. 解释单词、短语、句型。
            3. 帮用户造句、翻译、纠错。
            4. 根据用户水平给出简单清晰的讲解。
            5. 优先使用中文解释，必要时补充英文例句。

            回答规则：
            1. 默认简短回答，控制在 5 句内，或不超过 150 个汉字。
            2. 除非用户明确要求“详细讲解”，否则不要分 1、2、3、4 点展开。
            3. 单词问题默认只给：核心词义 + 一个记忆点 + 一个短例句 + 例句中文。
            4. 语法问题默认只给：规则 + 一个最短例句 + 例句中文。
            5. 每个知识点之间必须用空行隔开。
            6. 不回答和英语学习无关的问题，可以礼貌引导回英语学习。
            7. 语气像背词页辅导，不像老师写长篇讲义。
            """;

    private final DeepSeekEnglishChatClient deepSeekEnglishChatClient;
    private final ExampleEnrichmentProperties properties;
    private final ObjectMapper objectMapper;

    public EnglishChatService(
            DeepSeekEnglishChatClient deepSeekEnglishChatClient,
            ExampleEnrichmentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.deepSeekEnglishChatClient = deepSeekEnglishChatClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void streamEnglishChat(
            CurrentUser user,
            EnglishChatRequestDto request,
            OutputStream outputStream
    ) throws IOException {
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        if (user == null) {
            writeEvent(writer, "error", Map.of("message", "请先登录"));
            writeEvent(writer, "done", Map.of("reason", "unauthorized"));
            return;
        }
        if (!deepSeekEnglishChatClient.isConfigured()) {
            writeEvent(writer, "error", Map.of(
                    "message",
                    "当前运行中的 search-service 进程未读取到 "
                            + String.join(" / ", deepSeekEnglishChatClient.missingConfigurationKeys())
                            + "，请用已注入 DeepSeek 环境变量或本地配置文件的方式重启该服务"
            ));
            writeEvent(writer, "done", Map.of("reason", "not_configured"));
            return;
        }

        String userPrompt = normalize(request == null ? null : request.userPrompt());
        if (userPrompt.isBlank()) {
            writeEvent(writer, "error", Map.of("message", "请输入你的英语问题"));
            writeEvent(writer, "done", Map.of("reason", "blank_prompt"));
            return;
        }

        List<DeepSeekEnglishChatClient.EnglishChatTurn> history = normalizeHistory(request == null ? null : request.messages());
        String hiddenContextPrompt = buildHiddenContextPrompt(request == null ? null : request.questionContext());

        int maxOutputChars = properties.resolvedChatMaxOutputChars();
        final int[] emittedChars = {0};
        boolean[] finished = {false};
        try {
            deepSeekEnglishChatClient.streamEnglishChat(
                    new DeepSeekEnglishChatClient.EnglishChatPayload(
                            SYSTEM_PROMPT,
                            hiddenContextPrompt,
                            history,
                            userPrompt
                    ),
                    event -> {
                        if (finished[0]) {
                            return;
                        }
                        try {
                            switch (event.type()) {
                                case "token" -> {
                                    String token = event.payload() == null ? "" : event.payload();
                                    if (token.isEmpty() || emittedChars[0] >= maxOutputChars) {
                                        return;
                                    }
                                    int remain = maxOutputChars - emittedChars[0];
                                    String clipped = token.length() <= remain ? token : token.substring(0, remain);
                                    emittedChars[0] += clipped.length();
                                    writeEvent(writer, "token", Map.of("text", clipped));
                                }
                                case "error" -> {
                                    writeEvent(writer, "error", Map.of("message", fallbackErrorMessage(event.payload())));
                                    writeEvent(writer, "done", Map.of("reason", "error"));
                                    finished[0] = true;
                                }
                                case "done" -> {
                                    writeEvent(writer, "done", Map.of("reason", "completed"));
                                    finished[0] = true;
                                }
                                default -> {
                                }
                            }
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    }
            );
            if (!finished[0]) {
                writeEvent(writer, "done", Map.of("reason", "completed"));
            }
        } catch (RuntimeException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            writeEvent(writer, "error", Map.of("message", fallbackErrorMessage(exception.getMessage())));
            writeEvent(writer, "done", Map.of("reason", "failed"));
        }
    }

    private List<DeepSeekEnglishChatClient.EnglishChatTurn> normalizeHistory(List<EnglishChatMessageDto> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int maxMessages = properties.resolvedChatMaxTurns() * 2;
        List<DeepSeekEnglishChatClient.EnglishChatTurn> normalized = new ArrayList<>();
        for (EnglishChatMessageDto message : messages) {
            if (message == null) {
                continue;
            }
            String role = normalizeRole(message.role());
            String content = normalize(message.content());
            if (role == null || content.isBlank()) {
                continue;
            }
            normalized.add(new DeepSeekEnglishChatClient.EnglishChatTurn(role, content));
        }
        if (normalized.size() <= maxMessages) {
            return List.copyOf(normalized);
        }
        return List.copyOf(normalized.subList(normalized.size() - maxMessages, normalized.size()));
    }

    private String buildHiddenContextPrompt(EnglishQuestionContextDto context) {
        if (context == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (!normalize(context.word()).isBlank()) {
            parts.add("当前背词单词: " + normalize(context.word()));
        }
        if (!normalize(context.meaningCn()).isBlank()) {
            parts.add("当前中文释义: " + normalize(context.meaningCn()));
        }
        String exampleSentence = normalize(context.correctedExampleSentence());
        if (exampleSentence.isBlank()) {
            exampleSentence = normalize(context.exampleSentence());
        }
        if (!exampleSentence.isBlank()) {
            parts.add("当前例句: " + exampleSentence);
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "下面是当前背词题目的隐藏上下文，请在相关时优先结合这些信息回答，但不要逐字暴露“隐藏上下文”这个说法。\n"
                + String.join("\n", parts);
    }

    private String normalizeRole(String role) {
        String normalized = normalize(role).toLowerCase();
        if ("user".equals(normalized) || "assistant".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String fallbackErrorMessage(String message) {
        return normalize(message).isBlank() ? "AI 助手暂时不可用，请稍后再试" : normalize(message);
    }

    private void writeEvent(
            Writer writer,
            String eventName,
            Map<String, Object> payload
    ) throws IOException {
        Map<String, Object> safePayload = new LinkedHashMap<>(payload);
        writer.write("event: " + eventName + "\n");
        writer.write("data: " + objectMapper.writeValueAsString(safePayload) + "\n\n");
        writer.flush();
    }
}
