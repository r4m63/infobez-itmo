package com.itmo.infobez.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        /** Секрет HMAC. Задаётся только через переменную окружения, минимум 32 байта. */
        @NotBlank @Size(min = 32, message = "app.jwt.secret must be at least 32 characters") String secret,
        @NotBlank String issuer,
        Duration ttl) {

    public JwtProperties {
        if (ttl == null) {
            ttl = Duration.ofMinutes(15);
        }
    }
}
