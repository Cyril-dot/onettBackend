package com.marketPlace.MarketPlace.Config.Security.RateLimitingConfigs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rate.limit")
public class RateLimitingProperties {
    // her the number of requests allowed per minute is 100
    private int capacity = 100;
    // and the time window for refill is 1minute, that is after every minute
    private long refillSeconds = 100;
    // rate limiting enabled and it would be stored in memory
    private boolean enabled = true;
    // endpoints excluded from the rate limiting
    private String[] excludedPaths = {
            "/actuator/**",
            "/error",
            "/favicon.ico",
            "/.well-known/**",
            "/ping/**"
    };

    // track ip is enabled
    private boolean trackByIp = true;
    private long maxCacheSize = 100000;
}
