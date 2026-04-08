package com.marketPlace.MarketPlace.Config.Security.RateLimitingConfigs;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final RateLimitingProperties rateLimitingProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {

        // skip if ratelimiting is disabled
        if (!rateLimitingProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // to skip excluded paths
        String requestPath = request.getRequestURI();
        if (isExcludedPath(requestPath)){
            filterChain.doFilter(request, response);
            return;
        }

        // to get identifier
        String identifier = getIdentifier(request);

        boolean allowed = rateLimitingService.tryConsume(identifier);
        if (allowed){
            long availableTokens = rateLimitingService.getAvaliableTokens(identifier);
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitingProperties.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(availableTokens));
            response.setHeader("X-RateLimit-Reset", String.valueOf(
                    System.currentTimeMillis() / 1000 + (rateLimitingProperties.getRefillSeconds())
            ));

            log.debug("✅ Request allowed for {} - Remaining tokens: {}", identifier, availableTokens);
            filterChain.doFilter(request, response);
        } else {
            long secondsUntilRefill = rateLimitingService.getSecondsUntilRefil(identifier);

            log.warn("🚫 Rate limit exceeded for {} - Path: {} - Retry after: {}s",
                    identifier, requestPath, secondsUntilRefill);

            // Set response headers
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitingProperties.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(
                    System.currentTimeMillis() / 1000 + secondsUntilRefill
            ));
            response.setHeader("Retry-After", String.valueOf(secondsUntilRefill));

            // Write error response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Too Many Requests");
            errorResponse.put("message", String.format(
                    "Rate limit exceeded. You have exceeded the maximum of %d requests per %d minute(s). Please try again in %d seconds.",
                    rateLimitingProperties.getCapacity(),
                    rateLimitingProperties.getRefillSeconds(),
                    secondsUntilRefill
            ));
            errorResponse.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
            errorResponse.put("timestamp", LocalDateTime.now().toString());
            errorResponse.put("retryAfter", secondsUntilRefill);
            errorResponse.put("path", requestPath);

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            response.getWriter().flush();
        }

    }


    private String getIdentifier(HttpServletRequest request) {
        if (rateLimitingProperties.isTrackByIp()) {
            return getClientIP(request);
        } else {
            // Track by authenticated user ID
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
            // Fallback to IP if user is not authenticated
            return getClientIP(request);
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle multiple IPs (get the first one)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Check if the path should be excluded from rate limiting
     */
    private boolean isExcludedPath(String requestPath) {
        return Arrays.stream(rateLimitingProperties.getExcludedPaths())
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }


}
