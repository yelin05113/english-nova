package com.heima.englishnova.importservice;

import com.heima.englishnova.importservice.config.EnglishNovaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.heima.englishnova")
@EnableConfigurationProperties(EnglishNovaProperties.class)
public class ImportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImportServiceApplication.class, args);
    }
}
