package br.com.keyfalls.shared.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "keyfalls.security.jwt")
public record JwtProperties(
    @NotBlank String issuer,
    @NotBlank String secret,
    @NotNull Duration accessTokenExpiration
) {
    public JwtProperties {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("O segredo HMAC-SHA256 do JWT deve possuir no minimo 32 bytes (256 bits).");
        }
    }
}
