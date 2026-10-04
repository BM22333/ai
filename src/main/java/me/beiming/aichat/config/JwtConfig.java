package me.beiming.aichat.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class JwtConfig {
    @Value("${jwt.token-name}")
    private String tokenName;
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private int expiration;
    @Value("${jwt.refresh-token-name}")
    private String refreshTokenName;
    @Value("${jwt.refresh-expiration}")
    private int refreshExpiration;
}