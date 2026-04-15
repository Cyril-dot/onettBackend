package com.marketPlace.MarketPlace.Config.Security;

import com.marketPlace.MarketPlace.Config.Security.RateLimitingConfigs.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // =========================
                // CORS (IMPORTANT FIX)
                // =========================
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // =========================
                // CSRF DISABLE (REST API)
                // =========================
                .csrf(csrf -> csrf.disable())

                // =========================
                // STATELESS SESSION
                // =========================
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // =========================
                // AUTHORIZATION RULES
                // =========================
                .authorizeHttpRequests(auth -> auth

                        // 🔥 CRITICAL FIX: Allow preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ── AUTH ────────────────────────────────────────────────
                        .requestMatchers(
                                "/api/v1/users/register",
                                "/api/v1/users/login",
                                "/api/v1/sellers/register",
                                "/api/v1/sellers/login"
                        ).permitAll()

                        // ── WEBHOOKS ───────────────────────────────────────────
                        .requestMatchers(
                                "/api/v1/payments/orders/webhook",
                                "/api/v1/payments/product-listing/webhook"
                        ).permitAll()

                        // ── PUBLIC PRODUCTS ────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/**"
                        ).permitAll()

                        // ── INFRASTRUCTURE ─────────────────────────────────────
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/error/**",
                                "/actuator/**",
                                "/ws/**",
                                "/ws-meeting/**",
                                "/favicon.ico",
                                "/assets/**",
                                "/.well-known/**",
                                "/ping",
                                "/test/**"
                        ).permitAll()

                        // ── USER ROUTES ────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/payments/orders/*/submit",
                                "/api/v1/payments/product-listing/submit",
                                "/api/v1/product-requests/submit"
                        ).hasRole("USER")

                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/product-requests/*"
                        ).hasRole("USER")

                        // ── SELLER ROUTES ──────────────────────────────────────
                        .requestMatchers(
                                "/api/v1/payments/orders/admin/**",
                                "/api/v1/payments/product-listing/admin/**"
                        ).hasRole("SELLER")

                        // ── EVERYTHING ELSE ────────────────────────────────────
                        .anyRequest().authenticated()
                )

                // =========================
                // ERROR HANDLING
                // =========================
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.warn("🚫 Unauthorized: {} {}", request.getMethod(), request.getRequestURI());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"error\": \"Unauthorized\", \"message\": \"" +
                                            authException.getMessage() + "\"}"
                            );
                        })
                )

                // =========================
                // FILTERS
                // =========================
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // =========================
    // DISABLE DOUBLE REGISTRATION OF JWT FILTER
    // =========================
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {

        FilterRegistrationBean<JwtAuthenticationFilter> reg =
                new FilterRegistrationBean<>(filter);

        reg.setEnabled(false);
        return reg;
    }

    // =========================
    // PASSWORD ENCODER
    // =========================
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}