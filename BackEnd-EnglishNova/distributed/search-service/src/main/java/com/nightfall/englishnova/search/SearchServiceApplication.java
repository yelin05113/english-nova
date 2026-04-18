package com.nightfall.englishnova.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 搜索服务启动类。提供 Elasticsearch 全文检索与公共词库同步的启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.nightfall.englishnova")
public class SearchServiceApplication {

    /**
     * 搜索服务主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
