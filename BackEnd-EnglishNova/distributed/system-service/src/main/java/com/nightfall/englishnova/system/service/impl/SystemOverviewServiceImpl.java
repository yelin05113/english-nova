package com.nightfall.englishnova.system.service.impl;

import com.nightfall.englishnova.shared.dto.SystemModuleDto;
import com.nightfall.englishnova.shared.dto.SystemOverviewDto;
import com.nightfall.englishnova.system.service.SystemOverviewService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemOverviewServiceImpl implements SystemOverviewService {

    @Override
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
