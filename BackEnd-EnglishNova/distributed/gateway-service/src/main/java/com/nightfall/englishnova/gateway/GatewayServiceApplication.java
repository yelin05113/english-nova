package com.nightfall.englishnova.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关服务启动类。
 * <p>负责路由转发、JWT 鉴权过滤及统一入口管理。</p>
 */
@SpringBootApplication
public class GatewayServiceApplication {

    /**
     * 网关服务主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
