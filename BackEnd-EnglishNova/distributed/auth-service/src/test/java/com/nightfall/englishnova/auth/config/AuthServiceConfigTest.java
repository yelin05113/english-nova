package com.nightfall.englishnova.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceConfigTest {

    private static final Pattern BCRYPT_12_PATTERN = Pattern.compile("^\\$2[aby]\\$12\\$.*$");
    private static final Pattern BCRYPT_10_PATTERN = Pattern.compile("^\\$2[aby]\\$10\\$.*$");

    @Test
    void passwordEncoderUsesConfiguredDefaultStrengthAndRandomSalt() {
        AuthServiceConfig config = new AuthServiceConfig();
        PasswordEncoder encoder = config.passwordEncoder(new AuthServiceConfig.PasswordSecurityProperties(12));

        String firstHash = encoder.encode("Password123!");
        String secondHash = encoder.encode("Password123!");

        assertNotEquals(firstHash, secondHash);
        assertTrue(encoder.matches("Password123!", firstHash));
        assertTrue(BCRYPT_12_PATTERN.matcher(firstHash).matches());
        assertTrue(BCRYPT_12_PATTERN.matcher(secondHash).matches());
    }

    @Test
    void passwordEncoderSupportsStrengthOverride() {
        AuthServiceConfig config = new AuthServiceConfig();
        PasswordEncoder encoder = config.passwordEncoder(new AuthServiceConfig.PasswordSecurityProperties(10));

        String hash = encoder.encode("Password123!");

        assertTrue(encoder.matches("Password123!", hash));
        assertTrue(BCRYPT_10_PATTERN.matcher(hash).matches());
    }
}
