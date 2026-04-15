package com.heima.englishnova.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务启动类。
 * <p>负责用户注册、登录及 JWT 令牌管理等相关认证能力的启动入口。</p>
 */
@SpringBootApplication(scanBasePackages = "com.heima.englishnova")
public class AuthServiceApplication {

    /**
     * 认证服务主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
