package com.heima.englishnova.study;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 学习服务启动类。提供个人学习面板与进度统计的启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.heima.englishnova")
public class StudyServiceApplication {

    /**
     * 学习服务主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(StudyServiceApplication.class, args);
    }
}
