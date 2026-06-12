package com.nightfall.englishnova.quiz.controller;

import com.nightfall.englishnova.quiz.service.QuizService;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.auth.InternalAuthVerifier;
import com.nightfall.englishnova.shared.dto.WordNotebookSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuizController.class)
class QuizControllerWordNotebookRoutesTest {

    private static final CurrentUser USER = new CurrentUser(7L, "tester");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizService quizService;

    @MockitoBean
    private InternalAuthVerifier internalAuthVerifier;

    @BeforeEach
    void setUp() {
        when(internalAuthVerifier.require(any())).thenReturn(USER);
    }

    @Test
    void listWordNotebooksSupportsNewAndLegacyRoutes() throws Exception {
        WordNotebookSummaryDto notebook = new WordNotebookSummaryDto(
                1L,
                "核心词",
                3,
                true,
                OffsetDateTime.parse("2026-05-12T09:30:00Z")
        );
        when(quizService.listWordNotebooks(USER, "apple")).thenReturn(List.of(notebook));

        mockMvc.perform(get("/word-notebooks/list").param("word", "apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("核心词"));

        mockMvc.perform(get("/word-notebooks").param("word", "apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].containsWord").value(true));

        verify(quizService, times(2)).listWordNotebooks(USER, "apple");
    }

    @Test
    void createWordNotebookSupportsNewAndLegacyRoutes() throws Exception {
        String requestJson = """
                {"name":"收藏本"}
                """;
        WordNotebookSummaryDto notebook = new WordNotebookSummaryDto(
                2L,
                "收藏本",
                0,
                false,
                OffsetDateTime.parse("2026-05-12T10:00:00Z")
        );
        when(quizService.createWordNotebook(eq(USER), any())).thenReturn(notebook);

        mockMvc.perform(post("/word-notebooks/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2L))
                .andExpect(jsonPath("$.data.name").value("收藏本"));

        mockMvc.perform(post("/word-notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.wordCount").value(0));

        verify(quizService, times(2)).createWordNotebook(eq(USER), any());
    }
}
