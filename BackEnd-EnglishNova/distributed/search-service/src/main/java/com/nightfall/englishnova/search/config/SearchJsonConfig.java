package com.nightfall.englishnova.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 搜索服务 JSON 序列化配置类。
 */
@Configuration
public class SearchJsonConfig {

    /**
     * 提供 Jackson ObjectMapper Bean。
     *
     * @return ObjectMapper 实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
