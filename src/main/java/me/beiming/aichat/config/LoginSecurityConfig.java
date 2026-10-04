package me.beiming.aichat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "login.security")
public class LoginSecurityConfig {
    private int maxFailCount = 5;
    private long failTimeWindow = 300000;
    private long lockDuration = 1800000;
}