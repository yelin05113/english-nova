package com.heima.englishnova.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统服务启动类。
 * <p>提供系统概览、模块状态查询等系统级能力的启动入口。</p>
 */
@SpringBootApplication(scanBasePackages = "com.heima.englishnova")
public class SystemServiceApplication {

    /**
     * 系统服务主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SystemServiceApplication.class, args);
    }
}
