package com.heima.englishnova.system;

import com.heima.englishnova.shared.common.ApiResponse;
import com.heima.englishnova.shared.dto.SystemOverviewDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemOverviewService systemOverviewService;

    public SystemController(SystemOverviewService systemOverviewService) {
        this.systemOverviewService = systemOverviewService;
    }

    @GetMapping("/overview")
    public ApiResponse<SystemOverviewDto> overview() {
        return ApiResponse.success(systemOverviewService.getOverview());
    }
}
