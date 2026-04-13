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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerProductService {

    private final ProductRepo           productRepo;
    private final ProductImageRepo      productImageRepo;
    private final OrderItemRepo orderItemRepo;
    private final ProductVideoRepository      productVideoRepo;
    private final SellerRepo            sellerRepo;
    private final CartRepo              cartRepo;
    private final PreOrderRecordRepo   preOrderRecordRepo;
    private final ReviewRepo reviewRepo;
    private final ProductRequestRepository productRequestRepository;
    private final ProductCategoryRepo   categoryRepo;
    private final CategoryIconRepo      categoryIconRepo;
    private final CloudinaryService     cloudinaryService;
    private final NotificationService notificationService;

    // ═══════════════════════════════════════════════════════════
    //  CATEGORY MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request,
                                           MultipartFile iconFile,
                                           UUID sellerId) throws IOException {
        Seller seller = findSellerById(sellerId);

        log.info("Seller [{}] creating category: {}", seller.getStoreName(), request.getName());

        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? request.getSlug().toLowerCase()
                : request.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");

        if (categoryRepo.existsByNameIgnoreCase(request.getName()))
            throw new RuntimeException("Category '" + request.getName() + "' already exists");
        if (categoryRepo.existsBySlug(slug))
            throw new RuntimeException("Slug '" + slug + "' is already taken");

        CategoryIcon icon = null;
        if (iconFile != null && !iconFile.isEmpty()) {
            Map<String, Object> uploadResult = cloudinaryService.uploadImage(iconFile, "marketPlace/category_icons");
            icon = categoryIconRepo.save(CategoryIcon.builder()
                    .imageUrl((String) uploadResult.get("secure_url"))
                    .imagePublicId((String) uploadResult.get("public_id"))
                    .build());
        }

        ProductCategory category = ProductCategory.builder()
                .name(request.getName())
                .slug(slug)
                .icon(icon)
                .seller(seller)
                .build();

        ProductCategory saved = categoryRepo.save(category);
        log.info("Category created: [{}] by seller [{}]", saved.getName(), seller.getStoreName());
        return mapToCategoryResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID categoryId,
                                           CreateCategoryRequest request,
                                           MultipartFile iconFile,
                                           UUID sellerId) throws IOException {
        ProductCategory category = findCategoryById(categoryId);
        validateSellerOwnsCategory(category, sellerId);

        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? request.getSlug().toLowerCase()
                : request.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");

        category.setName(request.getName());
        category.setSlug(slug);

        if (iconFile != null && !iconFile.isEmpty()) {
            if (category.getIcon() != null) {
                cloudinaryService.deleteImage(category.getIcon().getImagePublicId());
                categoryIconRepo.delete(category.getIcon());
            }
            Map<String, Object> uploadResult = cloudinaryService.uploadImage(iconFile, "marketPlace/category_icons");
            CategoryIcon newIcon = categoryIconRepo.save(CategoryIcon.builder()
                    .imageUrl((String) uploadResult.get("secure_url"))
                    .imagePublicId((String) uploadResult.get("public_id"))
                    .build());
            category.setIcon(newIcon);
        }

        ProductCategory updated = categoryRepo.save(category);
        log.info("Category updated: [{}]", updated.getName());
        return mapToCategoryResponse(updated);
    }

    @Transactional
    public String deleteCategory(UUID categoryId, UUID sellerId) throws IOException {
        ProductCategory category = findCategoryById(categoryId);
        validateSellerOwnsCategory(category, sellerId);

        if (category.getIcon() != null)
            cloudinaryService.deleteImage(category.getIcon().getImagePublicId());

        categoryRepo.delete(category);
        log.info("Category deleted: [{}]", category.getName());
        return "Category '" + category.getName() + "' deleted successfully";
    }

    public List<CategoryResponse> getSellerCategories(UUID sellerId) {
        return categoryRepo.findBySellerId(sellerId)
                .stream()
                .map(this::mapToCategoryResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    //  PRODUCT MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public CreateProductResponse addProduct(CreateProductRequest request,
                                            List<MultipartFile> images,
                                            MultipartFile videoFile,
                                            UUID sellerId) throws IOException {
        Seller seller = findSellerById(sellerId);
        ProductCategory category = findCategoryById(request.getCategoryId());
        validateSellerOwnsCategory(category, sellerId);

        log.info("Seller [{}] adding product: {}", seller.getStoreName(), request.getName());

        StockStatus stockStatus = request.getStockStatus() != null
                ? request.getStockStatus() : StockStatus.IN_STOCK;
        validateStockStatus(stockStatus, request.getAvailableInDays());

        BigDecimal discountPrice = resolveDiscountPrice(
                request.isDiscounted(), request.getDiscountPercentage(), request.getPrice());

        Product product = Product.builder()
                .name(request.getName())
                .productDescription(request.getProductDescription())
                .price(request.getPrice())
                .brand(request.getBrand())
                .stock(request.getStock())
                .discounted(request.isDiscounted())
                .discountPercentage(request.isDiscounted() ? request.getDiscountPercentage() : null)
                .discountPrice(discountPrice)
                .productStatus(ProductStatus.ACTIVE)
                .stockStatus(stockStatus)
                .availableInDays(request.getAvailableInDays())
                .viewsCount(0)
                .seller(seller)
                .build();

        Product saved = productRepo.save(product);

        category.setProduct(saved);
        categoryRepo.save(category);

        // ── Images ───────────────────────────────────────────
        if (images != null && !images.isEmpty()) {
            List<ProductImage> productImages = uploadImages(images, saved, 0);
            productImageRepo.saveAll(productImages);
            saved.setImages(productImages);
        }

        // ── Video (optional) ─────────────────────────────────
        if (videoFile != null && !videoFile.isEmpty()) {
            ProductVideo video = uploadVideo(videoFile, saved);
            productVideoRepo.save(video);
            saved.setProductVideo(video);
            log.info("Video uploaded for product [{}]", saved.getName());
        }

        notificationService.notifyAllUsersNewProduct(product.getName(), product.getBrand(), product.getId());

        log.info("Product added: [{}] under category [{}] with {} image(s) and {} video",
                saved.getName(), category.getName(), saved.getImages().size(),
                saved.getProductVideo() != null ? "1" : "no");

        return mapToCreateProductResponse(saved, category);
    }

    @Transactional
    public CreateProductResponse updateProduct(UUID productId,
                                               UpdateProductRequest request,
                                               List<MultipartFile> newImages,
                                               MultipartFile videoFile,
                                               UUID sellerId) throws IOException {
        Product product = findProductById(productId);
        validateSellerOwnsProduct(product, sellerId);

        log.info("Seller [{}] updating product [{}]", sellerId, product.getName());

        if (request.getName() != null)               product.setName(request.getName());
        if (request.getProductDescription() != null) product.setProductDescription(request.getProductDescription());
        if (request.getBrand() != null)              product.setBrand(request.getBrand());
        if (request.getStock() != null)              product.setStock(request.getStock());

        // ── Discount recalculation ────────────────────────────
        BigDecimal effectivePrice      = request.getPrice()              != null ? request.getPrice()              : product.getPrice();
        BigDecimal effectivePercentage = request.getDiscountPercentage() != null ? request.getDiscountPercentage() : product.getDiscountPercentage();
        boolean    effectiveDiscounted = request.getIsDiscounted()       != null ? request.getIsDiscounted()       : Boolean.TRUE.equals(product.getDiscounted());

        if (request.getPrice() != null) product.setPrice(effectivePrice);

        if (!effectiveDiscounted) {
            product.setDiscounted(false);
            product.setDiscountPercentage(null);
            product.setDiscountPrice(null);
        } else {
            BigDecimal newDiscountPrice = resolveDiscountPrice(true, effectivePercentage, effectivePrice);
            product.setDiscounted(true);
            product.setDiscountPercentage(effectivePercentage);
            product.setDiscountPrice(newDiscountPrice);
            log.info("Recalculated discount price: {} ({}% off {})", newDiscountPrice, effectivePercentage, effectivePrice);
        }

        // ── Stock Status ──────────────────────────────────────
        if (request.getStockStatus() != null) {
            validateStockStatus(request.getStockStatus(), request.getAvailableInDays());
            product.setStockStatus(request.getStockStatus());
            // Clear availableInDays if switching to a status that doesn't need it
            if (request.getStockStatus() == StockStatus.IN_STOCK ||
                    request.getStockStatus() == StockStatus.OUT_OF_STOCK) {
                product.setAvailableInDays(null);
            } else {
                product.setAvailableInDays(request.getAvailableInDays());
            }
        }

        // ── Category ─────────────────────────────────────────
        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);
        if (request.getCategoryId() != null) {
            category = findCategoryById(request.getCategoryId());
            validateSellerOwnsCategory(category, sellerId);
            category.setProduct(product);
            categoryRepo.save(category);
        }

        // ── Image deletes ─────────────────────────────────────
        if (request.getImageIdsToDelete() != null && !request.getImageIdsToDelete().isEmpty()) {
            List<String> publicIdsToDelete = new ArrayList<>();
            for (Long imageId : request.getImageIdsToDelete()) {
                productImageRepo.findById(imageId).ifPresent(img -> {
                    publicIdsToDelete.add(img.getImagePublicId());
                    productImageRepo.delete(img);
                });
            }
            product.getImages().removeIf(img -> request.getImageIdsToDelete().contains(img.getId()));
            for (String publicId : publicIdsToDelete) {
                try { cloudinaryService.deleteImage(publicId); }
                catch (IOException e) { log.warn("Cloudinary image delete failed [{}]: {}", publicId, e.getMessage()); }
            }
        }

        // ── New images ────────────────────────────────────────
        if (newImages != null && !newImages.isEmpty()) {
            int maxOrder = product.getImages().stream()
                    .mapToInt(ProductImage::getDisplayOrder).max().orElse(-1);
            List<ProductImage> added = uploadImages(newImages, product, maxOrder + 1);
            productImageRepo.saveAll(added);
            product.getImages().addAll(added);
        }

        // ── Video: delete existing if requested or if a new one is being uploaded ──
        if (product.getProductVideo() != null &&
                (Boolean.TRUE.equals(request.getIsDeleteVideo()) || (videoFile != null && !videoFile.isEmpty()))) {
            deleteVideoFromCloudinaryAndDb(product);
        }

        // ── Video: upload new if provided ─────────────────────
        if (videoFile != null && !videoFile.isEmpty()) {
            ProductVideo video = uploadVideo(videoFile, product);
            productVideoRepo.save(video);
            product.setProductVideo(video);
            log.info("Video replaced for product [{}]", product.getName());
        }

        Product updated = productRepo.save(product);
        log.info("Product updated: [{}]", updated.getName());
        return mapToCreateProductResponse(updated, category);
    }

    @Transactional
    public CreateProductResponse replaceAllImages(UUID productId,
                                                  List<MultipartFile> newImages,
                                                  UUID sellerId) throws IOException {
        Product product = findProductById(productId);
        validateSellerOwnsProduct(product, sellerId);

        for (ProductImage img : product.getImages())
            cloudinaryService.deleteImage(img.getImagePublicId());

        productImageRepo.deleteByProductId(productId);
        product.getImages().clear();

        if (newImages != null && !newImages.isEmpty()) {
            List<ProductImage> uploaded = uploadImages(newImages, product, 0);
            productImageRepo.saveAll(uploaded);
            product.setImages(uploaded);
        }

        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);
        return mapToCreateProductResponse(productRepo.save(product), category);
    }

    @Transactional
    public CreateProductResponse uploadProductVideo(UUID productId,
                                                    MultipartFile videoFile,
                                                    UUID sellerId) throws IOException {
        Product product = findProductById(productId);
        validateSellerOwnsProduct(product, sellerId);

        if (videoFile == null || videoFile.isEmpty())
            throw new IllegalArgumentException("Video file cannot be null or empty");

        if (product.getProductVideo() != null) {
            deleteVideoFromCloudinaryAndDb(product);
            log.info("Replaced existing video for product [{}]", product.getName());
        }

        ProductVideo video = uploadVideo(videoFile, product);
        productVideoRepo.save(video);
        product.setProductVideo(video);

        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);
        return mapToCreateProductResponse(productRepo.save(product), category);
    }

    @Transactional
    public CreateProductResponse deleteProductVideo(UUID productId, UUID sellerId) throws IOException {
        Product product = findProductById(productId);
        validateSellerOwnsProduct(product, sellerId);

        if (product.getProductVideo() == null)
            throw new RuntimeException("Product [" + productId + "] has no video to delete");

        deleteVideoFromCloudinaryAndDb(product);
        log.info("Video deleted for product [{}]", product.getName());

        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);
        return mapToCreateProductResponse(productRepo.save(product), category);
    }

    @Transactional
    public CreateProductResponse updateStock(UUID productId, int newStock, UUID sellerId) {
        Product product = findProductById(productId);
        validateSellerOwnsProduct(product, sellerId);
        product.setStock(newStock);
        productRepo.save(product);
        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);
        return mapToCreateProductResponse(product, category);
    }

    @Transactional
    public CreateProductResponse updateProductStatus(UUID productId,
                                                     ProductStatus status,
                                                     UUID sellerId) {
        Product product = findProductById(productId);
        validateSellerOwnsProduct(product, sellerId);
        product.setProductStatus(status);
        productRepo.save(product);
        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);
        return mapToCreateProductResponse(product, category);
    }

    // ── UPDATE STOCK STATUS ONLY ─────────────────────────────
    @Transactional
    public CreateProductResponse updateStockStatus(UUID productId,
                                                   StockStatus stockStatus,
                                                   Integer availableInDays,
                                                   UUID sellerId) {
        Product product = findProductById(productId);
        validateSellerOwnsProduct(product, sellerId);

        validateStockStatus(stockStatus, availableInDays);
        product.setStockStatus(stockStatus);

        // Clear availableInDays if not relevant to new status
        if (stockStatus == StockStatus.IN_STOCK || stockStatus == StockStatus.OUT_OF_STOCK) {
            product.setAvailableInDays(null);
        } else {
            product.setAvailableInDays(availableInDays);
        }

        productRepo.save(product);
        log.info("Stock status updated to [{}] for product [{}]", stockStatus, product.getName());
        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);
        return mapToCreateProductResponse(product, category);
    }

    @Transactional
    public String deleteProduct(UUID productId, UUID sellerId) throws IOException {

        Product product = findProductById(productId);
        validateSellerOwnsProduct(product, sellerId);

        String productName = product.getName(); // capture before deletion

        log.info("🗑️  [deleteProduct] Starting deletion of product [{}] — '{}'", productId, productName);

        // ── 1. Delete images from Cloudinary + DB ────────────────────────────
        for (ProductImage img : product.getImages()) {
            try {
                cloudinaryService.deleteImage(img.getImagePublicId());
            } catch (Exception e) {
                log.warn("Failed to delete image [{}] from Cloudinary: {}", img.getImagePublicId(), e.getMessage());
            }
        }
        productImageRepo.deleteByProductId(productId);
        product.getImages().clear();
        log.debug("[deleteProduct] Images cleaned for product [{}]", productId);

        // ── 2. Delete video from Cloudinary + DB ─────────────────────────────
        if (product.getProductVideo() != null) {
            try {
                deleteVideoFromCloudinaryAndDb(product);
            } catch (Exception e) {
                log.warn("Failed to delete video for product [{}]: {}", productId, e.getMessage());
            }
        }
        log.debug("[deleteProduct] Video cleaned for product [{}]", productId);

        // ── 3. Remove cart items ──────────────────────────────────────────────
        int cartRowsDeleted = cartRepo.deleteByProductId(productId);
        log.debug("[deleteProduct] Removed {} cart item(s) for product [{}]", cartRowsDeleted, productId);

        // ── 4. Nullify order item product references ──────────────────────────
        orderItemRepo.nullifyProductReference(productId);
        log.debug("[deleteProduct] Nullified order_item product refs for product [{}]", productId);

        // ── 5. Nullify pre-order record product references ────────────────────
        // Pre-orders are financial records — preserve for auditing, just null the FK.
        preOrderRecordRepo.nullifyProductReference(productId);
        log.debug("[deleteProduct] Nullified pre_order_record product refs for product [{}]", productId);

        // ── 6. Delete reviews / ratings ───────────────────────────────────────
        reviewRepo.deleteByProductId(productId);
        log.debug("[deleteProduct] Deleted reviews for product [{}]", productId);

        // ── 7. Unlink category ────────────────────────────────────────────────
        categoryRepo.findByProductId(productId).ifPresent(cat -> {
            cat.setProduct(null);
            categoryRepo.save(cat);
        });
        log.debug("[deleteProduct] Unlinked category for product [{}]", productId);

        // ── 9. Final delete ───────────────────────────────────────────────────
        productRepo.delete(product);

        log.info("✅ [deleteProduct] Product [{}] '{}' fully deleted by seller [{}]", productId, productName, sellerId);

        return "Product '" + productName + "' deleted successfully";
    }

    // ═══════════════════════════════════════════════════════════
    //  READ / VIEW OPERATIONS
    // ═══════════════════════════════════════════════════════════

    public List<ProductSummaryResponse> getMyProducts(UUID sellerId) {
        return productRepo.findBySellerId(sellerId).stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> getMyProductsByStatus(UUID sellerId, ProductStatus status) {
        return productRepo.findBySellerIdAndProductStatus(sellerId, status).stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductDetailsResponse getProductDetails(UUID productId) {
        Product product = findProductById(productId);
        productRepo.incrementViewCount(productId);
        product.setViewsCount(product.getViewsCount() + 1);
        ProductCategory category = categoryRepo.findByProductId(productId).orElse(null);
        return mapToDetailsResponse(product, category);
    }

    public List<ProductSummaryResponse> getProductsByCategory(String slug) {
        ProductCategory category = categoryRepo.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Category not found: " + slug));
        return productRepo.findBySellerId(category.getSeller().getId()).stream()
                .filter(p -> {
                    ProductCategory cat = categoryRepo.findByProductId(p.getId()).orElse(null);
                    return cat != null && cat.getSlug().equals(slug);
                })
                .map(p -> mapToSummaryResponse(p, category))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> searchProducts(String keyword, String brand,
                                                       BigDecimal minPrice, BigDecimal maxPrice) {
        return productRepo.searchProducts(keyword, brand, minPrice, maxPrice, ProductStatus.ACTIVE.name()).stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> globalSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) throw new RuntimeException("Search keyword cannot be empty");
        return productRepo.globalSearch(keyword.trim()).stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> getTopTrendingProducts() {
        return productRepo.findTop10ByProductStatusOrderByViewsCountDesc(ProductStatus.ACTIVE).stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> getDiscountedProducts() {
        return productRepo.findByDiscountedTrueAndProductStatus(ProductStatus.ACTIVE).stream()
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> getLowStockProducts(UUID sellerId, int threshold) {
        return productRepo.findByStockLessThanEqual(threshold).stream()
                .filter(p -> p.getSeller().getId().equals(sellerId))
                .map(p -> mapToSummaryResponse(p, categoryRepo.findByProductId(p.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private void validateStockStatus(StockStatus stockStatus, Integer availableInDays) {
        if (stockStatus == StockStatus.COMING_SOON || stockStatus == StockStatus.PRE_ORDER) {
            if (availableInDays == null || availableInDays < 1) {
                throw new RuntimeException(
                        "availableInDays is required and must be >= 1 when stockStatus is " + stockStatus.name());
            }
        } else {
            if (availableInDays != null) {
                throw new RuntimeException(
                        "availableInDays should not be set when stockStatus is " + stockStatus.name());
            }
        }
    }

    private ProductVideo uploadVideo(MultipartFile file, Product product) throws IOException {
        Map<String, Object> result = cloudinaryService.uploadVideo(file, "marketPlace/products/videos");

        Integer durationSeconds = null;
        Object rawDuration = result.get("duration");
        if (rawDuration instanceof Number)
            durationSeconds = ((Number) rawDuration).intValue();

        return ProductVideo.builder()
                .cloudinaryPublicId((String) result.get("public_id"))
                .videoUrl((String) result.get("secure_url"))
                .originalFileName(file.getOriginalFilename())
                .format((String) result.get("format"))
                .fileSizeBytes(result.get("bytes") instanceof Number
                        ? ((Number) result.get("bytes")).longValue() : null)
                .durationSeconds(durationSeconds)
                .width(result.get("width")   instanceof Number ? ((Number) result.get("width")).intValue()  : null)
                .height(result.get("height") instanceof Number ? ((Number) result.get("height")).intValue() : null)
                .displayOrder(0)
                .isPrimary(true)
                .product(product)
                .build();
    }

    private void deleteVideoFromCloudinaryAndDb(Product product) throws IOException {
        ProductVideo video = product.getProductVideo();
        if (video == null) return;
        cloudinaryService.deleteVideo(video.getCloudinaryPublicId());
        productVideoRepo.delete(video);
        product.setProductVideo(null);
        log.info("Deleted video [{}] for product [{}]", video.getCloudinaryPublicId(), product.getName());
    }

    private BigDecimal resolveDiscountPrice(boolean isDiscounted,
                                            BigDecimal percentage,
                                            BigDecimal price) {
        if (!isDiscounted) return null;
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) <= 0
                || percentage.compareTo(new BigDecimal("100")) >= 0) {
            throw new RuntimeException(
                    "Discount percentage must be between 0 and 100 (exclusive) when isDiscounted is true");
        }
        return price
                .multiply(BigDecimal.ONE.subtract(
                        percentage.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<ProductImage> uploadImages(List<MultipartFile> files,
                                            Product product,
                                            int startOrder) throws IOException {
        List<ProductImage> result = new ArrayList<>();
        int order = startOrder;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            Map<String, Object> uploadResult = cloudinaryService.uploadImage(file, "marketPlace/products");
            result.add(ProductImage.builder()
                    .imageUrl((String) uploadResult.get("secure_url"))
                    .imagePublicId((String) uploadResult.get("public_id"))
                    .displayOrder(order++)
                    .product(product)
                    .build());
        }
        return result;
    }

    private Seller findSellerById(UUID sellerId) {
        return sellerRepo.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not found: " + sellerId));
    }

    private Product findProductById(UUID productId) {
        return productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
    }

    private ProductCategory findCategoryById(UUID categoryId) {
        return categoryRepo.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found: " + categoryId));
    }

    private void validateSellerOwnsProduct(Product product, UUID sellerId) {
        if (!product.getSeller().getId().equals(sellerId))
            throw new RuntimeException("You do not have permission to modify this product");
    }

    private void validateSellerOwnsCategory(ProductCategory category, UUID sellerId) {
        if (!category.getSeller().getId().equals(sellerId))
            throw new RuntimeException("You do not have permission to modify this category");
    }

    // ─── Mappers ─────────────────────────────────────────────

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

    private CreateProductResponse mapToCreateProductResponse(Product p, ProductCategory category) {
        return CreateProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .brand(p.getBrand())
                .stock(p.getStock())
                .discounted(Boolean.TRUE.equals(p.getDiscounted()))
                .discountPercentage(Boolean.TRUE.equals(p.getDiscounted()) ? p.getDiscountPercentage() : null)
                .discountPrice(Boolean.TRUE.equals(p.getDiscounted()) ? p.getDiscountPrice() : null)
                .productStatus(p.getProductStatus().name())
                .stockStatus(p.getStockStatus())
                .availableInDays(p.getAvailableInDays())
                .category(category != null ? mapToCategoryResponse(category) : null)
                .images(p.getImages().stream().map(this::mapImage).collect(Collectors.toList()))
                .video(mapVideo(p.getProductVideo()))
                .createdAt(p.getCreatedAt())
                .build();
    }

    private ProductSummaryResponse mapToSummaryResponse(Product p, ProductCategory category) {
        String primaryImage = p.getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);

        boolean isDiscounted = Boolean.TRUE.equals(p.getDiscounted());

        return ProductSummaryResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .isDiscounted(isDiscounted)
                .discountPercentage(isDiscounted ? p.getDiscountPercentage() : null)
                .discountPrice(isDiscounted ? p.getDiscountPrice() : null)
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

    private ProductDetailsResponse mapToDetailsResponse(Product p, ProductCategory category) {
        boolean isDiscounted = Boolean.TRUE.equals(p.getDiscounted());

        return ProductDetailsResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .productDescription(p.getProductDescription())
                .isDiscounted(isDiscounted)
                .discountPercentage(isDiscounted ? p.getDiscountPercentage() : null)
                .discountPrice(isDiscounted ? p.getDiscountPrice() : null)
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
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}