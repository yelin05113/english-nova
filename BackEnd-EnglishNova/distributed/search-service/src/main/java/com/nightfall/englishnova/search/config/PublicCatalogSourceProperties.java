package com.nightfall.englishnova.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "english-nova.search.public-catalog")
public record PublicCatalogSourceProperties(
        String externalDir
) {

    public String resolvedExternalDir() {
        return externalDir == null || externalDir.isBlank()
                ? ".local/public-catalog"
                : externalDir.trim();
    }
}
