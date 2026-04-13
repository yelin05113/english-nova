package com.heima.englishnova.study.controller;

import com.heima.englishnova.shared.common.ApiResponse;
import com.heima.englishnova.shared.dto.StudyAgendaDto;
import com.heima.englishnova.shared.dto.StudyProgressDto;
import com.heima.englishnova.study.service.StudyAgendaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyAgendaService studyAgendaService;

    public StudyController(StudyAgendaService studyAgendaService) {
        this.studyAgendaService = studyAgendaService;
    }

    @GetMapping("/agenda")
    public ApiResponse<StudyAgendaDto> agenda(HttpServletRequest request) {
        return ApiResponse.success(studyAgendaService.getTodayAgenda(request));
    }

    @GetMapping("/progress")
    public ApiResponse<StudyProgressDto> progress(HttpServletRequest request) {
        return ApiResponse.success(studyAgendaService.getProgress(request));
    }
}
