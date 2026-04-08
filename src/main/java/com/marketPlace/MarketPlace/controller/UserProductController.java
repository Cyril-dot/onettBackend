package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Service.UserProductService;
import com.marketPlace.MarketPlace.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class UserProductController {

    private final UserProductService userProductService;

    // ═══════════════════════════════════════════════════════════
    // HOME PAGE
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/products/home
     * Returns the full home page feed: featured, new arrivals, trending,
     * discounted products, and all categories in one shot.
     */
    @GetMapping("/home")
    public ResponseEntity<ApiResponse<HomePageResponse>> getHomePage() {
        log.info("GET getHomePage");

        HomePageResponse response = userProductService.getHomePage();

        log.debug("Home page feed assembled successfully");

        return ResponseEntity.ok(ApiResponse.success("Home page loaded", response));
    }

    /**
     * GET /api/v1/products/featured
     * Top-10 most viewed active products.
     */
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getFeaturedProducts() {
        log.info("GET getFeaturedProducts");

        List<ProductSummaryResponse> products = userProductService.getFeaturedProducts();

        log.debug("Returning {} featured products", products.size());

        return ResponseEntity.ok(ApiResponse.success("Featured products", products));
    }

    /**
     * GET /api/v1/products/new-arrivals
     * Latest 10 active products ordered by creation date.
     */
    @GetMapping("/new-arrivals")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getNewArrivals() {
        log.info("GET getNewArrivals");

        List<ProductSummaryResponse> products = userProductService.getNewArrivals();

        log.debug("Returning {} new arrivals", products.size());

        return ResponseEntity.ok(ApiResponse.success("New arrivals", products));
    }

    /**
     * GET /api/v1/products/trending
     * Top-10 trending active products by view count.
     */
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getTrendingProducts() {
        log.info("GET getTrendingProducts");

        List<ProductSummaryResponse> products = userProductService.getTrendingProducts();

        log.debug("Returning {} trending products", products.size());

        return ResponseEntity.ok(ApiResponse.success("Trending products", products));
    }

    /**
     * GET /api/v1/products/discounted
     * All active products with a discount applied.
     */
    @GetMapping("/discounted")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getDiscountedProducts() {
        log.info("GET getDiscountedProducts");

        List<ProductSummaryResponse> products = userProductService.getDiscountedProducts();

        log.debug("Returning {} discounted products", products.size());

        return ResponseEntity.ok(ApiResponse.success("Discounted products", products));
    }

    // ═══════════════════════════════════════════════════════════
    // UPCOMING PRODUCTS
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/products/upcoming
     * Returns both COMING_SOON and PRE_ORDER products grouped together,
     * each list sorted by availableInDays ascending (soonest first).
     */
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<UpcomingProductsResponse>> getUpcomingProducts() {
        log.info("GET getUpcomingProducts");

        UpcomingProductsResponse response = userProductService.getUpcomingProducts();

        log.debug("Upcoming products — comingSoon: {}, preOrder: {}",
                response.getTotalComingSoon(), response.getTotalPreOrder());

        return ResponseEntity.ok(ApiResponse.success("Upcoming products", response));
    }

    /**
     * GET /api/v1/products/coming-soon
     * Only COMING_SOON products, sorted by availableInDays ascending.
     */
    @GetMapping("/coming-soon")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getComingSoonProducts() {
        log.info("GET getComingSoonProducts");

        List<ProductSummaryResponse> products = userProductService.getComingSoonProducts();

        log.debug("Returning {} COMING_SOON products", products.size());

        return ResponseEntity.ok(ApiResponse.success("Coming soon products", products));
    }

    /**
     * GET /api/v1/products/pre-order
     * Only PRE_ORDER products, sorted by availableInDays ascending.
     */
    @GetMapping("/pre-order")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getPreOrderProducts() {
        log.info("GET getPreOrderProducts");

        List<ProductSummaryResponse> products = userProductService.getPreOrderProducts();

        log.debug("Returning {} PRE_ORDER products", products.size());

        return ResponseEntity.ok(ApiResponse.success("Pre-order products", products));
    }

    // ═══════════════════════════════════════════════════════════
    // CATEGORIES
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/products/categories
     * All product categories.
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        log.info("GET getAllCategories");

        List<CategoryResponse> categories = userProductService.getAllCategories();

        log.debug("Returning {} categories", categories.size());

        return ResponseEntity.ok(ApiResponse.success("Categories", categories));
    }

    /**
     * GET /api/v1/products/categories/{slug}
     * All active products under a specific category slug.
     */
    @GetMapping("/categories/{slug}")
    public ResponseEntity<ApiResponse<CategoryProductsResponse>> getProductsByCategory(
            @PathVariable String slug
    ) {
        log.info("GET getProductsByCategory | slug={}", slug);

        CategoryProductsResponse response = userProductService.getProductsByCategory(slug);

        log.debug("Category [{}] has {} products", slug, response.getTotalProducts());

        return ResponseEntity.ok(ApiResponse.success("Category products", response));
    }

    /**
     * GET /api/v1/products/brand/{brand}
     * All active products for a given brand (case-insensitive).
     */
    @GetMapping("/brand/{brand}")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getProductsByBrand(
            @PathVariable String brand
    ) {
        log.info("GET getProductsByBrand | brand={}", brand);

        List<ProductSummaryResponse> products = userProductService.getProductsByBrand(brand);

        log.debug("Returning {} products for brand={}", products.size(), brand);

        return ResponseEntity.ok(ApiResponse.success("Brand products", products));
    }

    /**
     * GET /api/v1/products/price-range?min=10.00&max=500.00
     * Active products within a price range.
     */
    @GetMapping("/price-range")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getProductsByPriceRange(
            @RequestParam BigDecimal min,
            @RequestParam BigDecimal max
    ) {
        log.info("GET getProductsByPriceRange | min={} | max={}", min, max);

        List<ProductSummaryResponse> products = userProductService.getProductsByPriceRange(min, max);

        log.debug("Returning {} products in price range {}-{}", products.size(), min, max);

        return ResponseEntity.ok(ApiResponse.success("Products in price range", products));
    }

    /**
     * GET /api/v1/products/store/{sellerId}
     * Full store page for a seller — their info, categories, and active products.
     */
    @GetMapping("/store/{sellerId}")
    public ResponseEntity<ApiResponse<SellerStoreResponse>> getSellerStore(
            @PathVariable UUID sellerId
    ) {
        log.info("GET getSellerStore | sellerId={}", sellerId);

        SellerStoreResponse response = userProductService.getSellerStore(sellerId);

        log.debug("Store loaded | sellerId={} | storeName={} | productCount={}",
                sellerId, response.getStoreName(), response.getTotalProducts());

        return ResponseEntity.ok(ApiResponse.success("Seller store", response));
    }

    // ═══════════════════════════════════════════════════════════
    // PRODUCT DETAILS
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/products/{productId}
     * Full product details with view count increment and related products.
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailsResponse>> getProductDetails(
            @PathVariable UUID productId
    ) {
        log.info("GET getProductDetails | productId={}", productId);

        ProductDetailsResponse response = userProductService.getProductDetails(productId);

        log.debug("Product details returned | productId={} | views={}", productId, response.getViewsCount());

        return ResponseEntity.ok(ApiResponse.success("Product details", response));
    }

    // ═══════════════════════════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/products/search/global?keyword=sneakers
     * Full-text global product search.
     */
    @GetMapping("/search/global")
    public ResponseEntity<ApiResponse<SearchResultResponse>> globalSearch(
            @RequestParam String keyword
    ) {
        log.info("GET globalSearch | keyword={}", keyword);

        SearchResultResponse response = userProductService.globalSearch(keyword);

        log.debug("Global search '{}' returned {} results", keyword, response.getTotalResults());

        return ResponseEntity.ok(ApiResponse.success("Search results", response));
    }

    /**
     * GET /api/v1/products/search?keyword=&brand=&minPrice=&maxPrice=&categorySlug=
     * Filtered product search — all params optional; returns all active products if none provided.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SearchResultResponse>> searchWithFilters(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String categorySlug
    ) {
        log.info("GET searchWithFilters | keyword={} | brand={} | minPrice={} | maxPrice={} | categorySlug={}",
                keyword, brand, minPrice, maxPrice, categorySlug);

        SearchResultResponse response =
                userProductService.searchWithFilters(keyword, brand, minPrice, maxPrice, categorySlug);

        log.debug("Filtered search returned {} results", response.getTotalResults());

        return ResponseEntity.ok(ApiResponse.success("Search results", response));
    }

    /**
     * GET /api/v1/products/search/location?location=Accra&keyword=shoes
     * Search products by seller location, with an optional keyword filter.
     */
    @GetMapping("/search/location")
    public ResponseEntity<ApiResponse<SearchResultResponse>> searchByLocation(
            @RequestParam String location,
            @RequestParam(required = false) String keyword
    ) {
        log.info("GET searchByLocation | location={} | keyword={}", location, keyword);

        SearchResultResponse response = userProductService.searchByLocation(location, keyword);

        log.debug("Location search [{}] returned {} results", location, response.getTotalResults());

        return ResponseEntity.ok(ApiResponse.success("Location search results", response));
    }
}