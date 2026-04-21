package com.nightfall.englishnova.study.controller;

import com.nightfall.englishnova.shared.common.ApiResponse;
import com.nightfall.englishnova.shared.dto.StudyAgendaDto;
import com.nightfall.englishnova.shared.dto.StudyProgressDto;
import com.nightfall.englishnova.study.service.StudyAgendaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习相关 HTTP 接口控制器。提供今日学习计划与学习进度统计等端点。
 */
@RestController
@RequestMapping({"/api/study", "/study"})
public class StudyController {

    private final StudyAgendaService studyAgendaService;

    /**
     * 构造函数。
     *
     * @param studyAgendaService 学习计划服务
     */
    public StudyController(StudyAgendaService studyAgendaService) {
        this.studyAgendaService = studyAgendaService;
    }

    /**
     * 获取今日学习计划。
     *
     * @param request HTTP 请求
     * @return 学习计划
     */
    @GetMapping("/agenda")
    public ApiResponse<StudyAgendaDto> agenda(HttpServletRequest request) {
        return ApiResponse.success(studyAgendaService.getTodayAgenda(request));
    }

    /**
     * 获取学习进度统计。
     *
     * @param request HTTP 请求
     * @return 学习进度
     */
    @GetMapping("/progress")
    public ApiResponse<StudyProgressDto> progress(HttpServletRequest request) {
        return ApiResponse.success(studyAgendaService.getProgress(request));
    }
}
