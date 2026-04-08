package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.ProductStatus;
import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import com.marketPlace.MarketPlace.entity.Repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProductService {

    private final ProductRepo         productRepo;
    private final ProductCategoryRepo categoryRepo;
    private final SellerRepo          sellerRepo;

    // ═══════════════════════════════════════════════════════════
    // HOME PAGE
    // ═══════════════════════════════════════════════════════════

    public HomePageResponse getHomePage() {
        log.info("Loading home page feed");
        return HomePageResponse.builder()
                .featuredProducts(getFeaturedProducts())
                .newArrivals(getNewArrivals())
                .trendingProducts(getTrendingProducts())
                .discountedProducts(getDiscountedProducts())
                .categories(getAllCategories())
                .build();
    }

    public List<ProductSummaryResponse> getFeaturedProducts() {
        log.info("Fetching featured products");
        return productRepo.findTop10ByProductStatusOrderByViewsCountDesc(ProductStatus.ACTIVE)
                .stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> getNewArrivals() {
        log.info("Fetching new arrivals");
        return productRepo.findTop10ByProductStatusOrderByCreatedAtDesc(ProductStatus.ACTIVE)
                .stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> getTrendingProducts() {
        log.info("Fetching trending products");
        return productRepo.findTop10ByProductStatusOrderByViewsCountDesc(ProductStatus.ACTIVE)
                .stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> getDiscountedProducts() {
        log.info("Fetching discounted products");
        return productRepo.findByDiscountedTrueAndProductStatus(ProductStatus.ACTIVE)
                .stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // UPCOMING PRODUCTS  (COMING_SOON + PRE_ORDER)
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns ALL upcoming products — both COMING_SOON and PRE_ORDER —
     * sorted by availableInDays ascending so nearest arrivals appear first.
     */
    public UpcomingProductsResponse getUpcomingProducts() {
        log.info("Fetching all upcoming products (COMING_SOON + PRE_ORDER)");

        List<ProductSummaryResponse> comingSoon = fetchByStockStatus(StockStatus.COMING_SOON);
        List<ProductSummaryResponse> preOrder   = fetchByStockStatus(StockStatus.PRE_ORDER);

        log.info("Upcoming products — comingSoon: {}, preOrder: {}", comingSoon.size(), preOrder.size());

        return UpcomingProductsResponse.builder()
                .comingSoon(comingSoon)
                .preOrder(preOrder)
                .totalComingSoon(comingSoon.size())
                .totalPreOrder(preOrder.size())
                .build();
    }

    /**
     * Returns only COMING_SOON products sorted by availableInDays ascending.
     */
    public List<ProductSummaryResponse> getComingSoonProducts() {
        log.info("Fetching COMING_SOON products");

        List<ProductSummaryResponse> results = fetchByStockStatus(StockStatus.COMING_SOON);

        log.info("Found {} COMING_SOON products", results.size());
        return results;
    }

    /**
     * Returns only PRE_ORDER products sorted by availableInDays ascending.
     */
    public List<ProductSummaryResponse> getPreOrderProducts() {
        log.info("Fetching PRE_ORDER products");

        List<ProductSummaryResponse> results = fetchByStockStatus(StockStatus.PRE_ORDER);

        log.info("Found {} PRE_ORDER products", results.size());
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    // CATEGORIES
    // ═══════════════════════════════════════════════════════════

    public List<CategoryResponse> getAllCategories() {
        log.info("Fetching all categories");
        return categoryRepo.findAll()
                .stream()
                .map(this::mapToCategoryResponse)
                .collect(Collectors.toList());
    }

    public CategoryProductsResponse getProductsByCategory(String slug) {
        log.info("Fetching products for category slug: {}", slug);

        ProductCategory category = categoryRepo.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Category not found: " + slug));

        List<ProductSummaryResponse> products = productRepo.findByProductStatus(ProductStatus.ACTIVE)
                .stream()
                .filter(p -> {
                    ProductCategory cat = categoryRepo.findByProductId(p.getId()).orElse(null);
                    return cat != null && cat.getSlug().equals(slug);
                })
                .map(p -> mapToSummaryResponse(p, category))
                .collect(Collectors.toList());

        log.info("Found {} products in category [{}]", products.size(), slug);

        return CategoryProductsResponse.builder()
                .category(mapToCategoryResponse(category))
                .products(products)
                .totalProducts(products.size())
                .build();
    }

    public List<ProductSummaryResponse> getProductsByBrand(String brand) {
        log.info("Fetching products by brand: {}", brand);
        return productRepo.findByBrandIgnoreCaseAndProductStatus(brand, ProductStatus.ACTIVE)
                .stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> getProductsByPriceRange(BigDecimal min, BigDecimal max) {
        log.info("Fetching products between GHS {} and GHS {}", min, max);
        return productRepo.findByPriceBetweenAndProductStatus(min, max, ProductStatus.ACTIVE)
                .stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public SellerStoreResponse getSellerStore(UUID sellerId) {
        log.info("Fetching store for seller [{}]", sellerId);

        Seller seller = sellerRepo.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not found: " + sellerId));

        List<ProductSummaryResponse> products = productRepo
                .findBySellerIdAndProductStatus(sellerId, ProductStatus.ACTIVE)
                .stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());

        List<CategoryResponse> sellerCategories = categoryRepo.findBySellerId(sellerId)
                .stream()
                .map(this::mapToCategoryResponse)
                .collect(Collectors.toList());

        return SellerStoreResponse.builder()
                .sellerId(seller.getId())
                .storeName(seller.getStoreName())
                .storeDescription(seller.getStoreDescription())
                .sellerName(seller.getFullName())
                .location(seller.getLocation())
                .profilePicUrl(seller.getProfilePic() != null
                        ? seller.getProfilePic().getImageUrl() : null)
                .isVerified(seller.isVerified())
                .categories(sellerCategories)
                .products(products)
                .totalProducts(products.size())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // PRODUCT DETAILS
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public ProductDetailsResponse getProductDetails(UUID productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        if (product.getProductStatus() != ProductStatus.ACTIVE) {
            throw new RuntimeException("Product is not available");
        }

        productRepo.incrementViewCount(productId);
        product.setViewsCount(product.getViewsCount() + 1);

        log.info("User viewed product [{}]", product.getName());

        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);

        // Related products — same category, exclude current, limit 6
        List<ProductSummaryResponse> relatedProducts = new ArrayList<>();
        if (category != null) {
            relatedProducts = productRepo.findByProductStatus(ProductStatus.ACTIVE)
                    .stream()
                    .filter(p -> !p.getId().equals(productId))
                    .filter(p -> {
                        ProductCategory cat = categoryRepo.findByProductId(p.getId()).orElse(null);
                        return cat != null && cat.getId().equals(category.getId());
                    })
                    .limit(6)
                    .map(p -> mapToSummaryResponse(p, category))
                    .collect(Collectors.toList());
        }

        return mapToDetailsResponse(product, category, relatedProducts);
    }

    // ═══════════════════════════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════════════════════════

    public SearchResultResponse globalSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new RuntimeException("Search keyword cannot be empty");
        }

        String trimmed = keyword.trim().toLowerCase();
        log.info("Global search: '{}'", trimmed);

        List<ProductSummaryResponse> products = productRepo.globalSearch(trimmed)
                .stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());

        log.info("Global search '{}' returned {} results", trimmed, products.size());

        return SearchResultResponse.builder()
                .keyword(trimmed)
                .totalResults(products.size())
                .products(products)
                .build();
    }

    public SearchResultResponse searchWithFilters(String keyword,
                                                  String brand,
                                                  BigDecimal minPrice,
                                                  BigDecimal maxPrice,
                                                  String categorySlug) {

        boolean noFilters = (keyword == null || keyword.isBlank())
                && (brand == null || brand.isBlank())
                && minPrice == null
                && maxPrice == null
                && (categorySlug == null || categorySlug.isBlank());

        if (noFilters) {
            log.info("No filters provided — returning all active products");
            List<ProductSummaryResponse> all = productRepo.findByProductStatus(ProductStatus.ACTIVE)
                    .stream()
                    .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                    .collect(Collectors.toList());
            return SearchResultResponse.builder()
                    .keyword(null)
                    .totalResults(all.size())
                    .products(all)
                    .build();
        }

        log.info("Filtered search — keyword: '{}', brand: {}, price: {}-{}, category: {}",
                keyword, brand, minPrice, maxPrice, categorySlug);

        List<Product> results = productRepo.searchProducts(
                keyword, brand, minPrice, maxPrice, ProductStatus.ACTIVE.name());

        if (categorySlug != null && !categorySlug.isBlank()) {
            results = results.stream()
                    .filter(p -> {
                        ProductCategory cat = categoryRepo.findByProductId(p.getId()).orElse(null);
                        return cat != null && cat.getSlug().equalsIgnoreCase(categorySlug);
                    })
                    .collect(Collectors.toList());
        }

        List<ProductSummaryResponse> products = results.stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());

        log.info("Filtered search returned {} results", products.size());

        return SearchResultResponse.builder()
                .keyword(keyword)
                .totalResults(products.size())
                .products(products)
                .build();
    }

    public SearchResultResponse searchByLocation(String location, String keyword) {
        log.info("Location search — location: {}, keyword: {}", location, keyword);

        List<ProductSummaryResponse> products = productRepo
                .findByProductStatus(ProductStatus.ACTIVE)
                .stream()
                .filter(p -> p.getSeller() != null
                        && p.getSeller().getLocation() != null
                        && p.getSeller().getLocation()
                        .toLowerCase().contains(location.toLowerCase()))
                .filter(p -> keyword == null || keyword.isBlank()
                        || p.getName().toLowerCase().contains(keyword.toLowerCase())
                        || p.getBrand().toLowerCase().contains(keyword.toLowerCase()))
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());

        log.info("Location search [{}] returned {} results", location, products.size());

        return SearchResultResponse.builder()
                .keyword(keyword)
                .location(location)
                .totalResults(products.size())
                .products(products)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetches ACTIVE products with the given StockStatus,
     * sorted by availableInDays ascending (soonest first).
     */
    private List<ProductSummaryResponse> fetchByStockStatus(StockStatus stockStatus) {
        return productRepo.findByProductStatusAndStockStatus(ProductStatus.ACTIVE, stockStatus)
                .stream()
                .sorted((a, b) -> {
                    int daysA = a.getAvailableInDays() != null ? a.getAvailableInDays() : Integer.MAX_VALUE;
                    int daysB = b.getAvailableInDays() != null ? b.getAvailableInDays() : Integer.MAX_VALUE;
                    return Integer.compare(daysA, daysB);
                })
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE MAPPERS
    // ═══════════════════════════════════════════════════════════

    private ProductSummaryResponse mapToSummaryResponse(Product p, ProductCategory category) {
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
                .discountPercentage(p.getDiscountPercentage())
                .discountPrice(p.getDiscountPrice())
                .brand(p.getBrand())
                .productStatus(p.getProductStatus().name())
                .stockStatus(p.getStockStatus())
                .availableInDays(p.getAvailableInDays())
                .stock(p.getStock())
                .viewsCount(p.getViewsCount())
                .primaryImageUrl(primaryImage)
                .hasVideo(p.getProductVideo() != null)
                .categoryName(category != null ? category.getName() : null)
                .categorySlug(category != null ? category.getSlug() : null)
                .build();
    }

    private ProductDetailsResponse mapToDetailsResponse(Product p,
                                                        ProductCategory category,
                                                        List<ProductSummaryResponse> relatedProducts) {
        return ProductDetailsResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .productDescription(p.getProductDescription())
                .isDiscounted(p.getDiscounted())
                .discountPercentage(p.getDiscountPercentage())
                .discountPrice(p.getDiscountPrice())
                .stock(p.getStock())
                .brand(p.getBrand())
                .productStatus(p.getProductStatus().name())
                .stockStatus(p.getStockStatus())
                .availableInDays(p.getAvailableInDays())
                .viewsCount(p.getViewsCount())
                .images(p.getImages().stream().map(this::mapImage).collect(Collectors.toList()))
                .video(mapVideo(p.getProductVideo()))
                .category(category != null ? mapToCategoryResponse(category) : null)
                .seller(p.getSeller() != null ? mapSeller(p.getSeller()) : null)
                .relatedProducts(relatedProducts)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private ProductImageResponse mapImage(ProductImage img) {
        return ProductImageResponse.builder()
                .id(img.getId())
                .imageUrl(img.getImageUrl())
                .imagePublicId(img.getImagePublicId())
                .displayOrder(img.getDisplayOrder())
                .build();
    }

    private ProductVideoResponse mapVideo(ProductVideo video) {
        if (video == null) return null;
        return ProductVideoResponse.builder()
                .id(video.getId())
                .videoUrl(video.getVideoUrl())
                .thumbnailUrl(video.getThumbnailUrl())
                .cloudinaryPublicId(video.getCloudinaryPublicId())
                .originalFileName(video.getOriginalFileName())
                .format(video.getFormat())
                .fileSizeBytes(video.getFileSizeBytes())
                .durationSeconds(video.getDurationSeconds())
                .width(video.getWidth())
                .height(video.getHeight())
                .isPrimary(video.getIsPrimary())
                .build();
    }

    private CategoryResponse mapToCategoryResponse(ProductCategory cat) {
        return CategoryResponse.builder()
                .id(cat.getId())
                .name(cat.getName())
                .slug(cat.getSlug())
                .icon(cat.getIcon() != null ? CategoryIconResponse.builder()
                        .id(cat.getIcon().getId())
                        .imageUrl(cat.getIcon().getImageUrl())
                        .imagePublicId(cat.getIcon().getImagePublicId())
                        .build() : null)
                .build();
    }

    private SellerSummaryResponse mapSeller(Seller seller) {
        return SellerSummaryResponse.builder()
                .id(seller.getId())
                .fullName(seller.getFullName())
                .storeName(seller.getStoreName())
                .location(seller.getLocation())
                .profilePic(seller.getProfilePic() != null ? ProfilePicResponse.builder()
                        .id(seller.getProfilePic().getId())
                        .imageUrl(seller.getProfilePic().getImageUrl())
                        .imagePublicId(seller.getProfilePic().getImagePublicId())
                        .build() : null)
                .build();
    }
}