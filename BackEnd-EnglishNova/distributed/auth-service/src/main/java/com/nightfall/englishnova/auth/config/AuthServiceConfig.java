package com.nightfall.englishnova.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证服务配置类。
 * <p>提供密码编码器 Bean 及 JWT 相关配置属性的绑定。</p>
 */
@Configuration
@EnableConfigurationProperties(AuthServiceConfig.JwtProperties.class)
public class AuthServiceConfig {

    /**
     * 提供密码编码器 Bean，使用 BCrypt 算法对用户密码进行加密与校验。
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 配置属性记录类，绑定前缀为 {@code english-nova.jwt} 的配置项。
     *
     * @param secret          JWT 签名密钥
     * @param expirationHours 令牌过期小时数
     */
    @ConfigurationProperties(prefix = "english-nova.jwt")
    public record JwtProperties(
            String secret,
            long expirationHours
    ) {
    }
}
