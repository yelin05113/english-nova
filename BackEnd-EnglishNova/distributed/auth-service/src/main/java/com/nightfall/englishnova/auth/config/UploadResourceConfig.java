package com.nightfall.englishnova.auth.config;

import com.nightfall.englishnova.auth.service.UserAvatarStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private final UserAvatarStorageService avatarStorageService;

    public UploadResourceConfig(UserAvatarStorageService avatarStorageService) {
        this.avatarStorageService = avatarStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/images/**")
                .addResourceLocations(avatarStorageService.resourceLocation());
    }
}
