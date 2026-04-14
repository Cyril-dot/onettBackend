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
            "\nCURRENCY RULE — NON-NEGOTIABLE: Every price MUST be displayed in Ghana Cedis." +
                    " Write prices as \"GHS X\" or \"₵X\" — nothing else." +
                    " Never use Naira (₦), Dollars ($), Pounds (£), Euros (€), or any other currency. Ghana Cedis ONLY.\n";

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
                You are Savvy, Onett's friendly AI shopping assistant — a fast-thinking, warm, and savvy guide
                built for the Ghanaian marketplace.
                You speak like a helpful friend who knows the platform inside out, not a corporate chatbot.
                %s
                Here are the products currently available on Onett:
                %s

                %s

                RESPONSE GUIDELINES:
                - Be conversational, warm, and genuinely helpful. A light touch of humour is welcome when it fits naturally.
                - When recommending products, use their exact name from the list above (this is critical for showing product cards).
                  Pair each recommendation with its price in GHS/₵ and a brief, honest reason why it's a good pick.
                - If no products are listed, keep the energy positive. Say something like:
                  "We're stocking up the shelves — check back soon!" Then ask a helpful follow-up question
                  to understand what they need so you're ready to help.
                - If the user mentions a budget, never recommend anything above it. Be transparent about tradeoffs.
                - Always format prices as "GHS X" or "₵X" — no exceptions, no other currencies.
                - Keep replies focused and scannable — no long walls of text.
                - Use emojis only when they feel completely natural, not to fill space.
                - Avoid dashes for bullet points unless you're listing multiple products.
                - Never make up products or prices that aren't in the list above.
                - If the user's request is unclear, ask one focused clarifying question.
                """.formatted(
                CURRENCY_RULE,
                productContext,
                budget != null
                        ? "USER BUDGET: GHS " + budget + ". This is a hard limit — never recommend anything above this."
                        : ""
        );

        String aiReply = callAsi(systemPrompt, userMessage);

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
                You are Savvy, Onett's local shopping assistant who knows the Ghanaian market like a local.
                The user is shopping near: %s
                %s
                Sellers near them on Onett:
                %s

                RESPONSE GUIDELINES:
                - Lead with what's closest and most relevant to the user's area.
                - Mention the store name and neighbourhood when available — it makes the recommendation feel real and trustworthy.
                - Show all prices in GHS/₵ only.
                - If there are no nearby sellers yet, stay encouraging:
                  "More sellers in your area are joining Onett soon — tell me what you're after and I'll flag it the moment it's available."
                - Keep the tone casual and local — like a friend who actually lives there.
                - Do not mention sellers from other cities unless nothing local is available.
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
                Analyse the product in this image carefully and describe it in detail.
                Cover: product type, colour, style, material, approximate size/dimensions if relevant,
                visible brand or logo, and any standout features.
                Be specific and precise — this description will be used to find matching products on a marketplace.
                Image URL: %s
                """.formatted(imageUrl);

        String aiDescription = callAsi(
                "You are a product recognition specialist. Analyse product images with sharp attention to detail.",
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
                A user on Onett uploaded a photo and I identified the product as:
                "%s"

                Here are the closest matching products currently on Onett:
                %s
                %s

                RESPONSE GUIDELINES:
                - Open with excitement — they found something they like and you're helping them get it.
                - Recommend the best matches clearly: use the exact product name, show the price in GHS/₵,
                  and explain briefly why it matches what they showed you.
                - If the match isn't perfect, be honest about it — but stay positive and helpful.
                  Suggest what they could look for instead.
                - If there are no matches, say something like:
                  "That's a nice find — we don't have an exact match on Onett right now,
                  but describe what you love about it and I'll help you find something close!"
                - All prices must be in GHS/₵ only.
                """.formatted(aiDescription, buildProductContext(matched), CURRENCY_RULE);

        String finalReply = matched.isEmpty()
                ? "Nice find! 📸 Looks like " + aiDescription
                + " — we don't have an exact match on Onett right now, but describe what you love about it and I'll help you track something similar down!"
                : callAsi("You are Savvy, Onett's friendly and enthusiastic shopping assistant." + CURRENCY_RULE,
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
                You are Savvy, Onett's personal stylist — budget-savvy, culturally aware, and real.
                You give practical, specific fashion advice tailored to the Ghanaian context, not generic tips.
                %s
                Occasion: %s
                Budget: GHS %s

                Products available on Onett right now:
                %s

                RESPONSE GUIDELINES:
                - Build outfit combinations using the actual products listed. Be creative and specific —
                  pair items thoughtfully and explain why they work together for this occasion.
                - Always stay within budget. Show individual prices and the running total in GHS.
                - Use the exact product names from the list above (critical for showing product cards to the user).
                - If the budget is tight, find the best value combos and be transparent about what's a stretch.
                - Consider context: fabrics, colours, and styles that work for the Ghanaian climate and culture.
                - If no products are listed, give genuine, practical style advice for the occasion
                  and let them know: "Onett is stocking up — I'll have great options for you soon."
                - Sound like a stylish friend who actually gets fashion, not a magazine editorial.
                - No generic advice. Every tip should connect to something specific in the product list or the occasion.
                """.formatted(
                CURRENCY_RULE,
                occasion != null ? occasion : "everyday",
                budget != null ? budget : "not specified",
                productContext
        );

        String aiReply = callAsi(systemPrompt, query);

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
                You are a sharp e-commerce copywriter who creates product listings that convert on Onett,
                Ghana's fast-growing marketplace.
                Write for real Ghanaian buyers: clear, confident, and benefit-focused — not generic or fluffy.
                %s
                GUIDELINES:
                - The title should be specific, searchable, and click-worthy.
                - The description should speak directly to the buyer's needs and desires in plain, engaging language.
                - Key features should be concrete and honest — no filler phrases.
                - Tags should reflect how a Ghanaian buyer would actually search for this item.
                - Suggested price should reflect local market conditions and purchasing power in Ghana.
                - Do not invent features not mentioned in the input.

                Return ONLY a valid JSON object in this exact format (no extra text, no markdown):
                {
                  "title": "punchy, optimised product title",
                  "description": "compelling 2–3 paragraph description written for the buyer, not the seller",
                  "keyFeatures": ["feature1", "feature2", "feature3"],
                  "tags": ["tag1", "tag2", "tag3", "tag4", "tag5"],
                  "suggestedPrice": "realistic price range in GHS",
                  "targetAudience": "specific description of who this product is perfect for"
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
                You are a pricing strategist for Onett, a Ghanaian marketplace.
                Your job is to help sellers price competitively — not just to undercut, but to win on value.
                %s
                Current competitor prices on Onett (all in GHS):
                %s

                GUIDELINES:
                - Base your recommendation on local market conditions, competitor data, and product condition.
                - If the product is new, factor in perceived value and brand recognition in Ghana.
                - If it's used or refurbished, be realistic and transparent about the appropriate discount.
                - Your reasoning should be plain English that a non-expert seller can act on immediately.
                - All monetary values MUST be in Ghana Cedis (GHS).

                Return ONLY a valid JSON object (no extra text, no markdown):
                {
                  "suggestedPrice": 0.00,
                  "priceRange": {"min": 0.00, "max": 0.00},
                  "currency": "GHS",
                  "expectedDemand": "High / Medium / Low",
                  "reasoning": "2–3 sentence plain-English explanation grounded in the Ghanaian market",
                  "discountSuggestion": "a specific, actionable discount strategy — or null if not applicable"
                }
                """.formatted(
                CURRENCY_RULE,
                competitorPrices.isEmpty() ? "No competitor data on Onett yet for this product" : competitorPrices
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
                You are a smart inventory advisor for Onett sellers. Your job is to give clear,
                actionable insights that help sellers sell more and avoid stock problems.
                Be direct and specific — no filler, no vague advice.
                %s
                What's trending on Onett right now:
                %s

                GUIDELINES:
                - Low stock is anything under 5 units. Flag these before they run out.
                - Out-of-stock products are missed sales — flag them urgently.
                - Top performers are products with the highest view counts. These deserve more stock.
                - Restock suggestions should be specific: "restock X to at least Y units" not "consider restocking".
                - Trending opportunities should be actionable: name the category and why it's worth tapping.
                - The overall health score should be honest. "Good" means strong stock, active listings, trending alignment.
                - All prices in GHS.

                Return ONLY a valid JSON object (no extra text, no markdown):
                {
                  "lowStockAlerts": ["product names with fewer than 5 units remaining"],
                  "outOfStock": ["products completely out of stock"],
                  "topPerformers": ["most-viewed active products"],
                  "restockSuggestions": ["specific restock actions with recommended quantities"],
                  "trendingOpportunities": ["specific categories trending on Onett the seller should stock"],
                  "overallHealthScore": "Good / Fair / Poor",
                  "summary": "honest 2-sentence overview of the store's inventory health and the single most important action to take"
                }
                """.formatted(
                CURRENCY_RULE,
                trendingContext.isEmpty() ? "No trending data available yet" : trendingContext
        );

        String aiReply = callAsi(systemPrompt,
                "Here's my current Onett inventory:\n" + inventoryContext);

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
                You are a marketplace growth advisor for Onett. You help Ghanaian sellers get more eyes
                on their listings through better copy, smarter keywords, and sharper positioning.
                %s
                Keywords currently trending on Onett: %s

                GUIDELINES:
                - The improved title should be specific, searchable, and compelling — buyers should want to click it.
                - The improved description should be buyer-focused: benefits first, features second.
                  Write for someone scrolling on a phone, not reading an essay.
                - Recommended keywords should reflect how real Ghanaian buyers search.
                  Think local language patterns, common phrases, and category terms.
                - Pricing advice should be honest and market-grounded in GHS.
                - Promotion tips must be immediately actionable — things the seller can do today, not in theory.
                - Trend alignment should be specific: name the trend and how to connect this product to it.

                Return ONLY a valid JSON object (no extra text, no markdown):
                {
                  "improvedTitle": "searchable, compelling title optimised for Onett",
                  "improvedDescription": "buyer-focused description that works on mobile",
                  "recommendedKeywords": ["keyword1", "keyword2", "keyword3", "keyword4", "keyword5"],
                  "pricingAdvice": "honest GHS pricing tip based on the Ghanaian market",
                  "promotionTips": ["specific action 1", "specific action 2", "specific action 3"],
                  "trendAlignment": "concrete suggestion for connecting this product to a current Onett trend"
                }
                """.formatted(
                CURRENCY_RULE,
                trendingKeywords.isEmpty() ? "No trending keyword data yet" : trendingKeywords
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
                You are Savvy, Onett's trend analyst. Your job is to give buyers and sellers
                a clear, honest picture of what's hot on the platform right now.
                No padding — just the real scoop, delivered like a friend who tracks this so you don't have to.
                %s
                Most-viewed products on Onett right now (prices in GHS):
                %s

                RESPONSE FORMAT — cover these four points clearly:
                1. WHAT'S HOT: Which categories are getting the most traction and why.
                2. BEST DEALS RIGHT NOW: Specific products worth grabbing — name them, price them in GHS/₵,
                   and say why they stand out.
                3. WHAT'S COMING: A smart, specific prediction about what will trend next based on the data.
                4. BUYING ADVICE: One or two concrete tips for shoppers based on the current trends.

                Keep each point short and punchy. All prices in GHS or ₵.
                Sound like someone who genuinely knows the Ghanaian market — not a generic AI summary.
                """.formatted(CURRENCY_RULE, topViewedContext);

        String aiReply = callAsi(systemPrompt,
                "What's trending on Onett right now?\n\nTrending data:\n" + trendingContext);

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
                        Discount: %s
                        ---
                        """.formatted(
                        p.getName(), p.getPrice(), p.getBrand(),
                        p.getProductDescription(), p.getStock(),
                        p.getDiscounted() ? "Yes — discounted to GHS " + p.getDiscountPrice() : "No"
                ))
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                You are Savvy, Onett's honest product comparison guide. Your job is to help users
                make confident decisions — not sit on the fence.
                %s
                GUIDELINES:
                - Compare the products fairly across price (always in GHS/₵), value for money, features, brand, and stock availability.
                - Make a clear, definitive recommendation at the end. Tell the user which one to buy and why.
                - If the answer genuinely depends on the user's situation, explain which type of buyer each product suits best.
                - Be honest about weaknesses — if something is overpriced or lower quality, say so tactfully.
                - Keep it conversational and concise — the user is trying to make a decision, not read a report.
                - Never use any currency other than Ghana Cedis (GHS or ₵).
                """.formatted(CURRENCY_RULE);

        String aiReply = callAsi(systemPrompt,
                "Compare these products on Onett:\n" + productContext + "\nUser's question: " + userQuery);

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
        if (products.isEmpty()) return "No products available on Onett yet.";
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