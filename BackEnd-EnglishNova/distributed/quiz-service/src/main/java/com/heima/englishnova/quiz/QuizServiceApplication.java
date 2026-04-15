package com.heima.englishnova.quiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 斩词服务启动类。负责斩词会话管理与作答流程的启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.heima.englishnova")
public class QuizServiceApplication {

    /**
     * 斩词服务主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(QuizServiceApplication.class, args);
    }
}
