package com.heima.englishnova.importservice.controller;

import com.heima.englishnova.importservice.service.ImportTaskService;
import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.auth.RequestUserExtractor;
import com.heima.englishnova.shared.common.ApiResponse;
import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.dto.ImportTaskDto;
import com.heima.englishnova.shared.dto.ImportTaskRequest;
import com.heima.englishnova.shared.enums.WordImportPlatform;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 导入相关 HTTP 接口控制器。提供导入平台预设查询、任务创建与文件上传等端点。
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportTaskService importTaskService;

    /**
     * 构造函数。
     *
     * @param importTaskService 导入任务服务
     */
    public ImportController(ImportTaskService importTaskService) {
        this.importTaskService = importTaskService;
    }

    /**
     * 查询所有导入平台预设。
     *
     * @return 预设列表
     */
    @GetMapping("/presets")
    public ApiResponse<List<ImportPresetDto>> presets() {
        return ApiResponse.success(importTaskService.listPresets());
    }

    /**
     * 查询当前用户的导入任务列表。
     *
     * @param request HTTP 请求
     * @return 导入任务列表
     */
    @GetMapping("/tasks")
    public ApiResponse<List<ImportTaskDto>> tasks(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(importTaskService.listTasks(user));
    }

    /**
     * 创建异步导入任务。
     *
     * @param request       导入任务请求
     * @param servletRequest HTTP 请求
     * @return 导入任务 DTO
     */
    @PostMapping("/tasks")
    public ApiResponse<ImportTaskDto> createTask(@Valid @RequestBody ImportTaskRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(importTaskService.createTask(user, request));
    }

    /**
     * 通过文件上传直接导入词书。
     *
     * @param platform   导入平台
     * @param sourceName 来源名称
     * @param file       上传文件
     * @param request    HTTP 请求
     * @return 导入任务 DTO
     */
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportTaskDto> importFile(
            @RequestParam WordImportPlatform platform,
            @RequestParam(required = false) String sourceName,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(importTaskService.importFile(user, platform, sourceName, file));
    }
}
