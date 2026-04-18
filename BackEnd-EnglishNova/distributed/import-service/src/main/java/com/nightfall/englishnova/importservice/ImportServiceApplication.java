package com.nightfall.englishnova.importservice;

import com.nightfall.englishnova.importservice.config.EnglishNovaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 导入服务启动类。
 */
@SpringBootApplication(scanBasePackages = "com.nightfall.englishnova")
@EnableConfigurationProperties(EnglishNovaProperties.class)
public class ImportServiceApplication {

    /**
     * 主入口方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ImportServiceApplication.class, args);
    }
}
