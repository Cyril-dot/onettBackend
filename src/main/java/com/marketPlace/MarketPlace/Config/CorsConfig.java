package com.marketPlace.MarketPlace.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:3000",
                // ngrok — all subdomains, all TLDs
                "https://*.ngrok-free.app",
                "https://*.ngrok-free.dev",
                "https://*.ngrok.io",
                "https://*.ngrok.app",
                "https://*.ngrok.dev",
                "https://cyril-dot.github.io",
                "http://127.0.0.1:5500",
                "http://localhost:5500",
                "http://localhost:8081",
                "http://192.168.8.127:8081",
                "https://lovable.dev",
                "https://*.lovable.dev",
                "https://savvymarket.lovable.app",
                "https://*.lovable.app",
                "https://*.lovableproject.com",
                "https://onett-user-frontend.vercel.app"
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "ngrok-skip-browser-warning"
        ));

        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}