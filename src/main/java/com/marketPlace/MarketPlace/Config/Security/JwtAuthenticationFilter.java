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

        return path.startsWith("/api/v1/users/register") ||
                path.startsWith("/api/v1/users/login") ||
                path.startsWith("/api/v1/sellers/register") ||
                path.startsWith("/api/v1/sellers/login") ||

                path.startsWith("/api/v1/payments/orders/webhook") ||
                path.startsWith("/api/v1/payments/product-listing/webhook") ||

                path.startsWith("/api/v1/products") ||
                path.startsWith("/api/v1/payments/orders/verify") ||
                path.startsWith("/api/v1/payments/product-listing/verify") ||

                path.startsWith("/api/v1/auth") ||
                path.startsWith("/error") ||
                path.startsWith("/actuator") ||
                path.startsWith("/ws") ||
                path.startsWith("/ws-meeting") ||
                path.equals("/favicon.ico") ||
                path.startsWith("/.well-known") ||
                path.startsWith("/test");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var existingAuth = SecurityContextHolder.getContext().getAuthentication();

        // Skip if already authenticated
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
                var sellerOptional = sellerRepo.findByEmail(email);

                if (sellerOptional.isEmpty()) {
                    log.warn("❌ Seller not found: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }

                userDetails = new AdminPrincipal(sellerOptional.get());

            } else {
                var userOptional = userRepo.findByEmail(email);

                if (userOptional.isEmpty()) {
                    log.warn("❌ User not found: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }

                userDetails = new UserPrincipal(userOptional.get());
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