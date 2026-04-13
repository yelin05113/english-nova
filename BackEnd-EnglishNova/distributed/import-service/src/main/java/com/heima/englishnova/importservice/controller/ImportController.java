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

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportTaskService importTaskService;

    public ImportController(ImportTaskService importTaskService) {
        this.importTaskService = importTaskService;
    }

    @GetMapping("/presets")
    public ApiResponse<List<ImportPresetDto>> presets() {
        return ApiResponse.success(importTaskService.listPresets());
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ImportTaskDto>> tasks(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(importTaskService.listTasks(user));
    }

    @PostMapping("/tasks")
    public ApiResponse<ImportTaskDto> createTask(@Valid @RequestBody ImportTaskRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(importTaskService.createTask(user, request));
    }

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
