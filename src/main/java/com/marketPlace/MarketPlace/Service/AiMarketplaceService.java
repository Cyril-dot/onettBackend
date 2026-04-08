package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.SessionType;
import com.marketPlace.MarketPlace.entity.Repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiMarketplaceService {

    private final AiSessionRepo         aiSessionRepo;
    private final ProductRepo           productRepo;
    private final TrendingProductsRepo  trendingProductsRepo;
    private final ImageSearchLogRepo    imageSearchLogRepo;
    private final UserRepo              userRepo;
    private final SellerRepo            sellerRepo;
    private final CloudinaryService     cloudinaryService;
    private final RestTemplate          restTemplate;

    @Value("${asi.api.key}")
    private String asiApiKey;

    private static final String ASI_URL       = "https://api.asi1.ai/v1/chat/completions";
    private static final String MODEL         = "asi1";
    private static final String CURRENCY_RULE =
            "\nCURRENCY RULE (NON-NEGOTIABLE): All prices MUST be shown in Ghana Cedis." +
                    " Always write prices as \"GHS X\" or \"₵X\"." +
                    " NEVER use Naira (₦), Dollars ($), Pounds (£), or any other currency. Ghana Cedis ONLY.\n";

    // ═══════════════════════════════════════════════════════════
    // CORE — CALL ASI:ONE API
    // ═══════════════════════════════════════════════════════════

    private String callAsi(String systemPrompt, String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(asiApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userMessage)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(ASI_URL, entity, Map.class);
            List<Map> choices = (List<Map>) response.getBody().get("choices");
            Map message = (Map) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("ASI:One API call failed: {}", e.getMessage());
            throw new RuntimeException("AI service unavailable. Please try again.");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPER — Match products mentioned in an AI reply
    // ═══════════════════════════════════════════════════════════

    /**
     * Scans the AI reply text for product name mentions and returns
     * matching products so the frontend can show product cards with images.
     * Falls back to a keyword search on the original user message if nothing
     * was found in the reply.
     */
    private List<ProductSummaryResponse> extractMentionedProducts(
            String aiReply,
            String fallbackQuery,
            List<Product> allProducts) {

        List<Product> mentioned = allProducts.stream()
                .filter(p -> aiReply.toLowerCase().contains(p.getName().toLowerCase()))
                .limit(6)
                .collect(Collectors.toList());

        if (mentioned.isEmpty() && fallbackQuery != null && !fallbackQuery.isBlank()) {
            mentioned = productRepo.globalSearch(fallbackQuery.trim())
                    .stream()
                    .limit(6)
                    .collect(Collectors.toList());
        }

        return mentioned.stream()
                .map(this::mapToProductSummary)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // 1. AI SHOPPING ASSISTANT — General Chat
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public AiChatResponse chat(UUID userId, String userMessage, BigDecimal budget) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("AI chat for user [{}]: {}", user.getEmail(), userMessage);

        List<Product> allProducts = productRepo.findByProductStatus(
                com.marketPlace.MarketPlace.entity.Enums.ProductStatus.ACTIVE);

        String productContext = buildProductContext(allProducts);

        String systemPrompt = """
                You are Savvy, a fun and chill shopping assistant for a Ghanaian marketplace.
                You talk like a helpful, knowledgeable friend — not a corporate bot.
                Keep things warm, short, and real.
                %s
                Products available right now:
                %s

                %s

                How to respond:
                - Be conversational and upbeat. Use light humour when it fits.
                - If products are available, recommend them enthusiastically with name,
                  price in GHS/₵, and a quick reason why.
                - If no products are listed, DON'T say "inventory is empty" or sound robotic.
                  Instead, keep the vibe going — say something like "we're loading up the shelves soon!"
                  and keep asking helpful follow-up questions to understand what they want.
                - If the user has a budget, never suggest anything above it.
                - Always format prices as "GHS X" or "₵X" — no exceptions.
                - Keep replies concise — no walls of text.
                - Use emojis sparingly and only when they feel natural.
                - Never use bullet points with dashes unless listing products.
                - IMPORTANT: When you mention or recommend products, always use their exact names
                  as they appear in the product list above — this lets us show product images to the user.
                """.formatted(
                CURRENCY_RULE,
                productContext,
                budget != null
                        ? "The user's budget is GHS " + budget + ". Never recommend anything above this amount."
                        : ""
        );

        String aiReply = callAsi(systemPrompt, userMessage);

        // Match products mentioned in the reply → show cards with images on frontend
        List<ProductSummaryResponse> products = extractMentionedProducts(aiReply, userMessage, allProducts);

        log.info("AI chat: returning {} product card(s) for user [{}]", products.size(), user.getEmail());

        saveOrUpdateSession(user, SessionType.SHOPPING_ASSISTANT, budget, userMessage);

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.SHOPPING_ASSISTANT.name())
                .products(products.isEmpty() ? null : products)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 2. LOCATION-BASED PRODUCT SEARCH
    // ═══════════════════════════════════════════════════════════

    public AiChatResponse searchByLocation(UUID userId, String userLocation, String query) {
        log.info("Location search — user: [{}], location: {}, query: {}", userId, userLocation, query);

        List<Product> locationProducts = productRepo.searchProducts(
                        query, null, null, null,
                        com.marketPlace.MarketPlace.entity.Enums.ProductStatus.ACTIVE.name()
                ).stream()
                .filter(p -> p.getSeller() != null
                        && p.getSeller().getLocation() != null
                        && p.getSeller().getLocation().toLowerCase()
                        .contains(userLocation.toLowerCase()))
                .collect(Collectors.toList());

        String productContext = buildProductContext(locationProducts);

        String systemPrompt = """
                You are Savvy, a friendly shopping assistant who knows the local Ghanaian scene.
                The user is shopping near: %s
                %s
                Nearby products:
                %s

                Keep it local and personal — mention the store name and area.
                Always show prices in Ghana Cedis (GHS or ₵) — never any other currency.
                If nothing is nearby yet, be encouraging and let them know more sellers
                are joining the platform. Ask what they need so you're ready when stock arrives.
                Keep the tone casual and helpful, like a friend who knows the area well.
                """.formatted(userLocation, CURRENCY_RULE, productContext);

        String aiReply = callAsi(systemPrompt, query);

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.LOCATION_SEARCH.name())
                .products(locationProducts.stream()
                        .map(this::mapToProductSummary)
                        .collect(Collectors.toList()))
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 3. IMAGE-BASED PRODUCT SEARCH
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public AiChatResponse searchByImage(UUID userId, MultipartFile imageFile) throws IOException {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("Image search initiated by user [{}]", user.getEmail());

        Map uploadResult = cloudinaryService.uploadImage(imageFile, "marketPlace/image_searches");
        String imageUrl      = (String) uploadResult.get("secure_url");
        String imagePublicId = (String) uploadResult.get("public_id");

        String descriptionPrompt = """
                Look at this image URL and describe the product you see in detail.
                Include: product type, color, style, brand if visible, and any standout features.
                Be specific so we can match it to marketplace products.
                Image URL: %s
                """.formatted(imageUrl);

        String aiDescription = callAsi(
                "You are a product recognition AI. Describe products in images precisely.",
                descriptionPrompt
        );

        log.info("AI image description: {}", aiDescription);

        List<Product> matched = productRepo.globalSearch(extractKeyword(aiDescription));

        ImageSearchLog searchLog = ImageSearchLog.builder()
                .user(user)
                .imageUrl(imageUrl)
                .imagePublicId(imagePublicId)
                .aiDescription(aiDescription)
                .matchedProducts(matched)
                .build();
        imageSearchLogRepo.save(searchLog);

        String recommendPrompt = """
                A user uploaded a photo and I identified it as:
                "%s"

                Here are the matching products on the Ghanaian marketplace:
                %s
                %s
                Recommend the best matches in a friendly, excited tone.
                Show each product's price in GHS/₵ only.
                Explain briefly why each one fits what they showed you.
                If the match isn't perfect, be honest but positive about it.
                """.formatted(aiDescription, buildProductContext(matched), CURRENCY_RULE);

        String finalReply = matched.isEmpty()
                ? "Nice pic! 📸 I spotted what looks like " + aiDescription
                + " — we don't have an exact match right now, but check back soon or tell me more about what you're after!"
                : callAsi("You are Savvy, a fun and friendly Ghanaian shopping assistant." + CURRENCY_RULE,
                recommendPrompt);

        return AiChatResponse.builder()
                .reply(finalReply)
                .sessionType(SessionType.IMAGE_SEARCH.name())
                .products(matched.stream().map(this::mapToProductSummary).collect(Collectors.toList()))
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 4. FASHION & BUDGET ADVISOR
    // ═══════════════════════════════════════════════════════════

    public AiChatResponse fashionAndBudgetAdvice(UUID userId,
                                                 String query,
                                                 BigDecimal budget,
                                                 String occasion) {
        log.info("Fashion/budget advice for user [{}] — budget: {}, occasion: {}", userId, budget, occasion);

        List<Product> allProducts = productRepo.findByProductStatus(
                com.marketPlace.MarketPlace.entity.Enums.ProductStatus.ACTIVE);

        String productContext = buildProductContext(allProducts);

        String systemPrompt = """
                You are Savvy, a personal stylist and budget-savvy shopping friend on a Ghanaian marketplace.
                You give real, practical fashion advice — not generic tips.
                %s
                Occasion: %s
                Budget: GHS %s

                Available products:
                %s

                Your job:
                - Suggest outfit combos using the products listed. Be creative and specific.
                - Stay within the budget — always. Calculate the total in GHS.
                - Show all individual prices and totals in Ghana Cedis (GHS or ₵).
                - Explain why each piece works for the occasion in plain, fun language.
                - If the budget is tight, find the best value combos and be upfront about tradeoffs.
                - If no products are available yet, give genuine general style advice for the occasion
                  and let them know the marketplace is stocking up soon.
                - Sound like a stylish friend, not a fashion magazine.
                - IMPORTANT: Use exact product names from the list above so product cards can be shown.
                """.formatted(
                CURRENCY_RULE,
                occasion != null ? occasion : "everyday",
                budget != null ? budget : "not set",
                productContext
        );

        String aiReply = callAsi(systemPrompt, query);

        // Match products mentioned in the fashion advice reply
        List<ProductSummaryResponse> products = extractMentionedProducts(aiReply, query, allProducts);

        saveOrUpdateSession(userRepo.findById(userId).orElseThrow(), SessionType.FASHION_ADVISOR, budget, query);

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.FASHION_ADVISOR.name())
                .products(products.isEmpty() ? null : products)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 5. SELLER — AI LISTING GENERATOR
    // ═══════════════════════════════════════════════════════════

    public AiChatResponse generateProductListing(UUID sellerId,
                                                 String productName,
                                                 String basicDetails) {
        log.info("Generating AI listing for seller [{}] — product: {}", sellerId, productName);

        String systemPrompt = """
                You are a sharp e-commerce copywriter who writes listings that actually sell
                on a Ghanaian marketplace.
                No fluff, no corporate speak — just clear, compelling, buyer-focused copy.
                %s
                Return ONLY a JSON object in this exact format:
                {
                  "title": "punchy, optimized product title",
                  "description": "engaging product description in 2-3 short paragraphs that speaks to the buyer",
                  "keyFeatures": ["feature1", "feature2", "feature3"],
                  "tags": ["tag1", "tag2", "tag3", "tag4", "tag5"],
                  "suggestedPrice": "realistic price range in GHS (Ghana Cedis)",
                  "targetAudience": "who this is perfect for"
                }
                """.formatted(CURRENCY_RULE);

        String aiReply = callAsi(systemPrompt,
                "Product: " + productName + ". Details: " + basicDetails);

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.LISTING_GENERATOR.name())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 6. SELLER — PRICE SUGGESTION AI
    // ═══════════════════════════════════════════════════════════

    public AiChatResponse suggestPrice(UUID sellerId,
                                       String productName,
                                       String productDetails,
                                       String condition) {
        log.info("Price suggestion for seller [{}] — product: {}", sellerId, productName);

        List<Product> similar = productRepo.findByNameContainingIgnoreCase(productName);
        String competitorPrices = similar.stream()
                .map(p -> p.getName() + " → GHS " + p.getPrice())
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                You are a sharp pricing advisor for a Ghanaian marketplace.
                Give practical, data-driven pricing advice that helps sellers win.
                %s
                Competitor prices (all in GHS):
                %s

                Return ONLY a JSON object — all monetary values must be in Ghana Cedis (GHS):
                {
                  "suggestedPrice": 0.00,
                  "priceRange": {"min": 0.00, "max": 0.00},
                  "currency": "GHS",
                  "expectedDemand": "High/Medium/Low",
                  "reasoning": "plain-English explanation of why this price works for the Ghanaian market",
                  "discountSuggestion": "a smart discount strategy if applicable"
                }
                """.formatted(
                CURRENCY_RULE,
                competitorPrices.isEmpty() ? "No competitor data yet" : competitorPrices
        );

        String aiReply = callAsi(systemPrompt,
                "Product: " + productName
                        + ". Details: " + productDetails
                        + ". Condition: " + (condition != null ? condition : "New"));

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.PRICE_ADVISOR.name())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 7. SELLER — SMART INVENTORY ASSISTANT
    // ═══════════════════════════════════════════════════════════

    public AiChatResponse analyzeInventory(UUID sellerId) {
        Seller seller = sellerRepo.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        log.info("Inventory analysis for seller [{}]", seller.getStoreName());

        List<Product> products = productRepo.findBySellerId(sellerId);

        String inventoryContext = products.stream()
                .map(p -> "- %s | Stock: %d | Price: GHS %s | Views: %d | Status: %s"
                        .formatted(p.getName(), p.getStock(), p.getPrice(),
                                p.getViewsCount(), p.getProductStatus()))
                .collect(Collectors.joining("\n"));

        List<TrendingProducts> trending = trendingProductsRepo
                .findByExpiresAtAfterOrderByTrendingScoreDesc(LocalDateTime.now());
        String trendingContext = trending.stream()
                .map(t -> t.getProduct().getName() + " (score: " + t.getTrendingScore() + ")")
                .collect(Collectors.joining(", "));

        String systemPrompt = """
                You are a smart inventory advisor who gives Ghanaian sellers clear, actionable insights.
                Skip the corporate language — be direct and useful.
                %s
                What's trending on the platform right now:
                %s

                Return your analysis as JSON (all prices in GHS):
                {
                  "lowStockAlerts": ["products running low (stock under 5)"],
                  "outOfStock": ["products completely out"],
                  "topPerformers": ["most-viewed products"],
                  "restockSuggestions": ["what to restock and roughly how much"],
                  "trendingOpportunities": ["categories blowing up that the seller should tap into"],
                  "overallHealthScore": "Good/Fair/Poor",
                  "summary": "honest 2-sentence summary of where the inventory stands"
                }
                """.formatted(
                CURRENCY_RULE,
                trendingContext.isEmpty() ? "No trending data yet" : trendingContext
        );

        String aiReply = callAsi(systemPrompt,
                "Here's my current inventory:\n" + inventoryContext);

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.INVENTORY_ASSISTANT.name())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 8. SELLER — VISIBILITY IMPROVEMENT
    // ═══════════════════════════════════════════════════════════

    public AiChatResponse improveProductVisibility(UUID sellerId, UUID productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        log.info("Visibility improvement for product [{}] by seller [{}]", product.getName(), sellerId);

        List<TrendingProducts> trending = trendingProductsRepo
                .findByExpiresAtAfterOrderByTrendingScoreDesc(LocalDateTime.now());
        String trendingKeywords = trending.stream()
                .map(TrendingProducts::getKeyword)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        String systemPrompt = """
                You are a marketplace growth advisor who helps Ghanaian sellers get more eyes
                on their products. Be practical, specific, and skip the filler advice.
                %s
                Trending keywords right now: %s

                Return JSON (all prices in GHS):
                {
                  "improvedTitle": "a title that's searchable and click-worthy",
                  "improvedDescription": "rewritten description that sells the product better",
                  "recommendedKeywords": ["keyword1", "keyword2", "keyword3"],
                  "pricingAdvice": "honest pricing tip in GHS based on the Ghanaian market",
                  "promotionTips": ["3 specific things the seller can do today to boost visibility"],
                  "trendAlignment": "how to tweak the listing to ride current trends"
                }
                """.formatted(
                CURRENCY_RULE,
                trendingKeywords.isEmpty() ? "No trending data yet" : trendingKeywords
        );

        String aiReply = callAsi(systemPrompt,
                "Product: %s | Price: GHS %s | Views: %d | Description: %s"
                        .formatted(product.getName(), product.getPrice(),
                                product.getViewsCount(), product.getProductDescription()));

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.VISIBILITY_ADVISOR.name())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 9. TRENDING PRODUCTS ANALYZER
    // ═══════════════════════════════════════════════════════════

    public AiChatResponse analyzeTrends(UUID userId) {
        log.info("Trend analysis requested by user [{}]", userId);

        List<TrendingProducts> trending = trendingProductsRepo
                .findByExpiresAtAfterOrderByTrendingScoreDesc(LocalDateTime.now());

        List<Product> topViewed = productRepo.findTop10ByProductStatusOrderByViewsCountDesc(
                com.marketPlace.MarketPlace.entity.Enums.ProductStatus.ACTIVE);

        String trendingContext = trending.stream()
                .map(t -> "- %s | Score: %d | Keyword: %s"
                        .formatted(t.getProduct().getName(),
                                t.getTrendingScore(), t.getKeyword()))
                .collect(Collectors.joining("\n"));

        String topViewedContext = topViewed.stream()
                .map(p -> "- %s | Views: %d | Price: GHS %s"
                        .formatted(p.getName(), p.getViewsCount(), p.getPrice()))
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                You are Savvy, a trend-savvy marketplace analyst for the Ghanaian market who keeps it real.
                Give buyers and sellers the honest scoop on what's hot right now.
                %s
                Most-viewed products (prices in GHS):
                %s

                Break it down:
                1. What categories are on fire right now
                2. The best deals worth grabbing (mention prices in GHS/₵)
                3. What's likely to trend next (make a smart prediction)
                4. Quick buying advice based on the trends

                Keep it punchy and useful — like a friend who tracks this stuff so you don't have to.
                All prices must be shown in GHS or ₵.
                """.formatted(CURRENCY_RULE, topViewedContext);

        String aiReply = callAsi(systemPrompt,
                "What's trending right now?\n\nTrending data:\n" + trendingContext);

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.TREND_ANALYZER.name())
                .products(topViewed.stream().map(this::mapToProductSummary).collect(Collectors.toList()))
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 10. PRODUCT COMPARISON
    // ═══════════════════════════════════════════════════════════

    public AiChatResponse compareProducts(UUID userId, List<UUID> productIds, String userQuery) {
        log.info("Product comparison for user [{}] — {} products", userId, productIds.size());

        List<Product> products = productIds.stream()
                .map(id -> productRepo.findById(id)
                        .orElseThrow(() -> new RuntimeException("Product not found: " + id)))
                .collect(Collectors.toList());

        String productContext = products.stream()
                .map(p -> """
                        Product: %s
                        Price: GHS %s
                        Brand: %s
                        Description: %s
                        Stock: %d
                        Discounted: %s %s
                        ---
                        """.formatted(
                        p.getName(), p.getPrice(), p.getBrand(),
                        p.getProductDescription(), p.getStock(),
                        p.getDiscounted() ? "Yes → GHS " + p.getDiscountPrice() : "No", ""
                ))
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                You are Savvy, a straight-talking product comparison expert on a Ghanaian marketplace.
                Give an honest, clear comparison and tell the user which one to pick and why.
                %s
                Don't sit on the fence — make a real recommendation.
                Consider price (always shown in GHS/₵), value, features, and brand.
                Keep it conversational and concise.
                All prices must be in Ghana Cedis (GHS or ₵) — never any other currency.
                """.formatted(CURRENCY_RULE);

        String aiReply = callAsi(systemPrompt,
                "Compare these:\n" + productContext + "\nUser's question: " + userQuery);

        return AiChatResponse.builder()
                .reply(aiReply)
                .sessionType(SessionType.SHOPPING_ASSISTANT.name())
                .products(products.stream().map(this::mapToProductSummary).collect(Collectors.toList()))
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private String buildProductContext(List<Product> products) {
        if (products.isEmpty()) return "No products available yet.";
        return products.stream()
                .limit(30)
                .map(p -> "- %s | GHS %s%s | Brand: %s | Stock: %d | Seller: %s (%s)"
                        .formatted(
                                p.getName(),
                                p.getDiscounted() ? p.getDiscountPrice() : p.getPrice(),
                                p.getDiscounted() ? " (was GHS " + p.getPrice() + ")" : "",
                                p.getBrand(),
                                p.getStock(),
                                p.getSeller() != null ? p.getSeller().getStoreName() : "N/A",
                                p.getSeller() != null ? p.getSeller().getLocation() : "N/A"
                        ))
                .collect(Collectors.joining("\n"));
    }

    private ProductSummaryResponse mapToProductSummary(Product p) {
        String primaryImage = p.getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);

        return ProductSummaryResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .isDiscounted(p.getDiscounted())
                .discountPrice(p.getDiscountPrice())
                .brand(p.getBrand())
                .stock(p.getStock())
                .viewsCount(p.getViewsCount())
                .primaryImageUrl(primaryImage)
                .build();
    }

    private void saveOrUpdateSession(User user, SessionType type,
                                     BigDecimal budget, String lastMessage) {
        AiSession session = AiSession.builder()
                .user(user)
                .sessionType(type)
                .budget(budget)
                .createdAt(LocalDateTime.now())
                .lastActive(LocalDateTime.now())
                .metadata(Map.of("lastMessage", lastMessage))
                .build();
        aiSessionRepo.save(session);
    }

    private String extractKeyword(String aiDescription) {
        String[] words = aiDescription.split("\\s+");
        return Arrays.stream(words)
                .filter(w -> w.length() > 3)
                .limit(3)
                .collect(Collectors.joining(" "));
    }
}