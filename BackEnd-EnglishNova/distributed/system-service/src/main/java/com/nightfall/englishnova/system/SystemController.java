package com.nightfall.englishnova.system;

import com.nightfall.englishnova.shared.common.ApiResponse;
import com.nightfall.englishnova.shared.dto.SystemOverviewDto;
import com.nightfall.englishnova.system.service.SystemOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统信息 HTTP 接口控制器。
 * <p>提供系统概览、模块状态等公开查询端点。</p>
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemOverviewService systemOverviewService;

    /**
     * 构造注入系统概览服务。
     *
     * @param systemOverviewService 系统概览业务服务
     */
    public SystemController(SystemOverviewService systemOverviewService) {
        this.systemOverviewService = systemOverviewService;
    }

    /**
     * 获取系统概览信息接口。
     * <p>HTTP GET /api/system/overview</p>
     *
     * @return 系统概览数据响应
     */
    @GetMapping("/overview")
    public ApiResponse<SystemOverviewDto> overview() {
        return ApiResponse.success(systemOverviewService.getOverview());
    }
}
