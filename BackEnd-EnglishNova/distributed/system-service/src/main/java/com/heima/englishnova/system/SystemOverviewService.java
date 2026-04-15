package com.heima.englishnova.system;

import com.heima.englishnova.shared.dto.SystemModuleDto;
import com.heima.englishnova.shared.dto.SystemOverviewDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统概览业务服务。
 * <p>负责组装并返回系统基本信息、模块状态及支持特性列表。</p>
 */
@Service
public class SystemOverviewService {

    /**
     * 获取系统概览数据。
     *
     * @return 包含项目名称、描述、支持平台、模块状态及特性列表的概览 DTO
     */
    public SystemOverviewDto getOverview() {
        return new SystemOverviewDto(
                "English Nova",
                "personal vocabulary command center",
                List.of("Baicizhan", "Bubeidanci", "Shanbay", "Anki"),
                List.of(
                        new SystemModuleDto("auth-service", "account registration, login, and JWT issuance", "READY"),
                        new SystemModuleDto("import-service", "personal wordbook import and task persistence", "READY"),
                        new SystemModuleDto("quiz-service", "wordbook progress and slash quiz sessions", "READY"),
                        new SystemModuleDto("search-service", "public and private word retrieval over Elasticsearch", "READY"),
                        new SystemModuleDto("study-service", "user-specific study dashboard and progress summary", "READY"),
                        new SystemModuleDto("gateway-service", "JWT verification and service routing via Nacos", "READY")
                ),
                List.of("Per-user storage isolation", "JWT-based gateway auth", "Wordbook slash quiz", "Elasticsearch global search")
        );
    }
}
