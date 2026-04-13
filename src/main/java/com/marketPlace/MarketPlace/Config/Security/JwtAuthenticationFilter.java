// JwtAuthenticationFilter.java
package com.marketPlace.MarketPlace.Config.Security;

import com.marketPlace.MarketPlace.entity.Repo.SellerRepo;
import com.marketPlace.MarketPlace.entity.Repo.UserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepo userRepo;
    private final SellerRepo sellerRepo;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        return  // Auth
                path.startsWith("/api/v1/users/register") ||
                        path.startsWith("/api/v1/users/login")    ||
                        path.startsWith("/api/v1/sellers/register") ||
                        path.startsWith("/api/v1/sellers/login")  ||

                        // Webhooks
                        path.startsWith("/api/v1/payments/orders/webhook") ||
                        path.startsWith("/api/v1/payments/product-listing/webhook") ||

                        // Public GET — products only (verify/* routes removed: no longer public)
                        ("GET".equalsIgnoreCase(method) && path.startsWith("/api/v1/products")) ||

                        // Infrastructure
                        path.startsWith("/api/v1/auth")    ||
                        path.startsWith("/error")           ||
                        path.startsWith("/actuator")        ||
                        path.startsWith("/ws")              ||
                        path.startsWith("/ws-meeting")      ||
                        path.equals("/favicon.ico")         ||
                        path.startsWith("/.well-known")     ||
                        path.equals("/ping")                ||
                        path.startsWith("/test");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var existingAuth = SecurityContextHolder.getContext().getAuthentication();

        if (existingAuth != null
                && existingAuth.isAuthenticated()
                && !(existingAuth instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String header = request.getHeader("Authorization");

            if (header == null || !header.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = header.substring(7);
            String email = tokenService.getEmailFromAccessToken(token);
            String role  = tokenService.getRoleFromAccessToken(token);

            UserDetails userDetails;

            if ("SELLER".equals(role)) {
                userDetails = sellerRepo.findByEmail(email)
                        .map(AdminPrincipal::new)
                        .orElseGet(() -> {
                            log.warn("❌ Seller not found for email: {}", email);
                            return null;
                        });

            } else {
                userDetails = userRepo.findByEmail(email)
                        .map(UserPrincipal::new)
                        .orElseGet(() -> {
                            log.warn("❌ User not found for email: {}", email);
                            return null;
                        });
            }

            if (userDetails == null) {
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.info("✅ Authenticated: {} ({})", email, role);

        } catch (Exception e) {
            log.error("💥 JWT error: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}