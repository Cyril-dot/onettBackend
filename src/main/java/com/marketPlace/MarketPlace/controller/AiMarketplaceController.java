package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal;
import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.AiMarketplaceService;
import com.marketPlace.MarketPlace.dtos.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiMarketplaceController {

    private final AiMarketplaceService aiMarketplaceService;

    // ═══════════════════════════════════════════════════════════
    // 1. AI SHOPPING ASSISTANT — General Chat
    // ═══════════════════════════════════════════════════════════

    /**
     * POST /api/v1/ai/chat
     * Accessible by authenticated users (ROLE_USER)
     */
    @PostMapping("/chat")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AiChatResponse> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String message,
            @RequestParam(required = false) BigDecimal budget
    ) {
        UUID userId = principal.getUserId();
        AiChatResponse response = aiMarketplaceService.chat(userId, message, budget);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 2. LOCATION-BASED PRODUCT SEARCH
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/ai/search/location
     * Accessible by authenticated users (ROLE_USER)
     */
    @GetMapping("/search/location")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AiChatResponse> searchByLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String location,
            @RequestParam String query
    ) {
        UUID userId = principal.getUserId();
        AiChatResponse response = aiMarketplaceService.searchByLocation(userId, location, query);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 3. IMAGE-BASED PRODUCT SEARCH
    // ═══════════════════════════════════════════════════════════

    /**
     * POST /api/v1/ai/search/image
     * Multipart upload — accessible by authenticated users (ROLE_USER)
     */
    @PostMapping(value = "/search/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AiChatResponse> searchByImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("image") MultipartFile imageFile
    ) throws IOException {
        UUID userId = principal.getUserId();
        AiChatResponse response = aiMarketplaceService.searchByImage(userId, imageFile);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 4. FASHION & BUDGET ADVISOR
    // ═══════════════════════════════════════════════════════════

    /**
     * POST /api/v1/ai/fashion-advice
     * Accessible by authenticated users (ROLE_USER)
     */
    @PostMapping("/fashion-advice")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AiChatResponse> fashionAndBudgetAdvice(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String query,
            @RequestParam(required = false) BigDecimal budget,
            @RequestParam(required = false) String occasion
    ) {
        UUID userId = principal.getUserId();
        AiChatResponse response = aiMarketplaceService.fashionAndBudgetAdvice(userId, query, budget, occasion);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 5. SELLER — AI LISTING GENERATOR
    // ═══════════════════════════════════════════════════════════

    /**
     * POST /api/v1/ai/seller/generate-listing
     * Accessible by sellers (ROLE_SELLER)
     */
    @PostMapping("/seller/generate-listing")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AiChatResponse> generateProductListing(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam String productName,
            @RequestParam String basicDetails
    ) {
        UUID sellerId = principal.getSellerId();
        AiChatResponse response = aiMarketplaceService.generateProductListing(sellerId, productName, basicDetails);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 6. SELLER — PRICE SUGGESTION AI
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/ai/seller/suggest-price
     * Accessible by sellers (ROLE_SELLER)
     */
    @GetMapping("/seller/suggest-price")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AiChatResponse> suggestPrice(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam String productName,
            @RequestParam String productDetails,
            @RequestParam(required = false) String condition
    ) {
        UUID sellerId = principal.getSellerId();
        AiChatResponse response = aiMarketplaceService.suggestPrice(sellerId, productName, productDetails, condition);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 7. SELLER — SMART INVENTORY ASSISTANT
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/ai/seller/inventory-analysis
     * Accessible by sellers (ROLE_SELLER)
     */
    @GetMapping("/seller/inventory-analysis")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AiChatResponse> analyzeInventory(
            @AuthenticationPrincipal AdminPrincipal principal
    ) {
        UUID sellerId = principal.getSellerId();
        AiChatResponse response = aiMarketplaceService.analyzeInventory(sellerId);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 8. SELLER — VISIBILITY IMPROVEMENT
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/ai/seller/improve-visibility/{productId}
     * Accessible by sellers (ROLE_SELLER)
     */
    @GetMapping("/seller/improve-visibility/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AiChatResponse> improveProductVisibility(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId
    ) {
        UUID sellerId = principal.getSellerId();
        AiChatResponse response = aiMarketplaceService.improveProductVisibility(sellerId, productId);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 9. TRENDING PRODUCTS ANALYZER
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/ai/trends
     * Accessible by both users and sellers
     */
    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('USER', 'SELLER')")
    public ResponseEntity<AiChatResponse> analyzeTrends(
            @AuthenticationPrincipal Object principal
    ) {
        UUID id = resolveId(principal);
        AiChatResponse response = aiMarketplaceService.analyzeTrends(id);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // 10. PRODUCT COMPARISON
    // ═══════════════════════════════════════════════════════════

    /**
     * POST /api/v1/ai/compare
     * Accessible by authenticated users (ROLE_USER)
     */
    @PostMapping("/compare")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AiChatResponse> compareProducts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam List<UUID> productIds,
            @RequestParam String query
    ) {
        UUID userId = principal.getUserId();
        AiChatResponse response = aiMarketplaceService.compareProducts(userId, productIds, query);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER — resolve ID from either principal type
    // ═══════════════════════════════════════════════════════════

    private UUID resolveId(Object principal) {
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUserId();
        } else if (principal instanceof AdminPrincipal adminPrincipal) {
            return adminPrincipal.getSellerId();
        }
        throw new RuntimeException("Unknown principal type");
    }
}