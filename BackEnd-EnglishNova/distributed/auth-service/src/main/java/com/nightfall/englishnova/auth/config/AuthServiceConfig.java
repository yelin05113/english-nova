package com.nightfall.englishnova.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;

@Configuration
@EnableConfigurationProperties({
        AuthServiceConfig.JwtProperties.class,
        AuthServiceConfig.PasswordSecurityProperties.class
})
public class AuthServiceConfig {

    @Bean
    public PasswordEncoder passwordEncoder(PasswordSecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    @ConfigurationProperties(prefix = "english-nova.jwt")
    public record JwtProperties(
            String secret,
            long expirationHours
    ) {
    }

    @Validated
    @ConfigurationProperties(prefix = "english-nova.security.password")
    public record PasswordSecurityProperties(
            @Min(4)
            @Max(31)
            int bcryptStrength
    ) {
    }
}
