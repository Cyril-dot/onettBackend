package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import com.marketPlace.MarketPlace.entity.Repo.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserAdvancedProductService {

    private final UserRepo userRepo;
    private final ProductRequestService requestService;
    private final ProductRequestRepository requestRepository;
    private final UserProductRepository userProductRepository;
    private final CloudinaryService cloudinaryService;
    private final SellerRepo sellerRepo;
    private final UserProductImageRepository productImageRepository;


    // here user can request a product that is not available and it results in a 100cedis fee
    public UserProductResponse createUserProductRequest(
            UserProductRequest userProductRequest,
            UUID userId,
            UUID requestId,
            List<MultipartFile> images
    ) throws IOException {

        User user = userRepo.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ [UserAdvancedProductService] User not found — with id {}", userId);
                    return new RuntimeException("User not found");
                });

        ProductRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.error("❌ [UserAdvancedProductService] ProductRequest not found — id {}", requestId);
                    return new RuntimeException("ProductRequest not found with id: " + requestId);
                });

        // confirm the user has paid
        ProductRequestPayload verifyPayment = requestService.getProductRequest(request.getId(), user.getId());
        if (!verifyPayment.getPaid()) {
            log.error("❌ [UserAdvancedProductService] Payment not completed for this product request — with id {}", requestId);
            throw new RuntimeException("Payment not completed for this product request");
        }

        UserProduct product = new UserProduct();
        product.setName(userProductRequest.getName());
        product.setProductDescription(userProductRequest.getProductDescription());
        product.setBrand(userProductRequest.getBrand());
        product.setUser(user);
        product.setApprovalStatus(ApprovalStatus.PENDING);
        product.setProductRequest(request);
        product.setUpdateCount(0);
        product.setCreatedAt(LocalDateTime.now());

        UserProduct savedProduct = userProductRepository.save(product);

        if (images != null && !images.isEmpty()) {
            List<UserProductImage> productImages = uploadImages(images, savedProduct, 0);
            productImageRepository.saveAll(productImages);
            savedProduct.setImages(productImages);
        }

        return mapToUserProductResponse(savedProduct);
    }


    public List<RequestProductResponse> viewAllProductRequests(UUID sellerId) {

        log.info("📥 [UserAdvancedProductService] Fetching all product requests for sellerId={}", sellerId);

        sellerRepo.findById(sellerId)
                .orElseThrow(() -> {
                    log.error("❌ [UserAdvancedProductService] Seller not found — id={}", sellerId);
                    return new RuntimeException("Seller not found with id: " + sellerId);
                });

        List<ProductRequest> requests = requestRepository.findAll();
        List<UserProduct> products = userProductRepository.findAll();

        Map<UUID, UserProduct> productMap = products.stream()
                .collect(Collectors.toMap(
                        p -> p.getUser().getId(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        List<RequestProductResponse> response = requests.stream()
                .map(request -> {
                    UUID userId = request.getUser().getId();
                    UserProduct product = productMap.get(userId);

                    return RequestProductResponse.builder()
                            .userId(userId)
                            .name(request.getUser().getFullName())
                            .email(request.getUser().getEmail())
                            .productResponse(mapToUserProductResponse(product))
                            .payload(mapToPayload(request))
                            .build();
                })
                .toList();

        log.info("✅ [UserAdvancedProductService] Successfully fetched {} product requests for sellerId={}",
                response.size(), sellerId);

        return response;
    }


    public Page<RequestProductResponse> viewProductsByStatus(
            UUID sellerId,
            ApprovalStatus status,
            Pageable pageable
    ) {
        log.info("📥 [UserAdvancedProductService] Fetching product requests by status={} for sellerId={}",
                status, sellerId);

        sellerRepo.findById(sellerId)
                .orElseThrow(() -> {
                    log.error("❌ [UserAdvancedProductService] Seller not found — id={}", sellerId);
                    return new RuntimeException("Seller not found with id: " + sellerId);
                });

        Page<UserProduct> productPage =
                userProductRepository.findByApprovalStatus(status, pageable);

        Page<RequestProductResponse> responsePage = productPage.map(product -> {
            ProductRequest request = product.getProductRequest();

            return RequestProductResponse.builder()
                    .userId(product.getUser().getId())
                    .name(product.getUser().getFullName())
                    .email(product.getUser().getEmail())
                    .productResponse(mapToUserProductResponse(product))
                    .payload(request != null ? mapToPayload(request) : null)
                    .build();
        });

        log.info("✅ [UserAdvancedProductService] Retrieved {} products with status={} for sellerId={}",
                responsePage.getTotalElements(), status, sellerId);

        return responsePage;
    }


    public Page<RequestProductResponse> viewRecentProductRequests(Pageable pageable) {

        log.info("📥 [UserAdvancedProductService] Fetching recent product requests");

        Page<ProductRequest> requestPage =
                requestRepository.findAllByOrderByCreatedAtDesc(pageable);

        Page<RequestProductResponse> responsePage = requestPage.map(request -> {
            UserProduct product = userProductRepository
                    .findTopByUserIdOrderByCreatedAtDesc(request.getUser().getId())
                    .orElse(null);

            return RequestProductResponse.builder()
                    .userId(request.getUser().getId())
                    .name(request.getUser().getFullName())
                    .email(request.getUser().getEmail())
                    .productResponse(mapToUserProductResponse(product))
                    .payload(mapToPayload(request))
                    .build();
        });

        log.info("✅ [UserAdvancedProductService] Retrieved {} recent product requests",
                responsePage.getTotalElements());

        return responsePage;
    }


    public Page<RequestProductResponse> getUserProductRequests(UUID userId, Pageable pageable) {

        log.info("📥 Fetching product requests for userId={}", userId);

        Page<ProductRequest> requests = requestRepository.findByUserId(userId, pageable);

        return requests.map(request -> {
            UserProduct product = userProductRepository
                    .findTopByUserIdOrderByCreatedAtDesc(userId)
                    .orElse(null);

            return RequestProductResponse.builder()
                    .userId(userId)
                    .name(request.getUser().getFullName())
                    .email(request.getUser().getEmail())
                    .productResponse(mapToUserProductResponse(product))
                    .payload(mapToPayload(request))
                    .build();
        });
    }


    public Page<UserProductResponse> getUserProductsByStatus(
            UUID userId,
            ApprovalStatus status,
            Pageable pageable
    ) {
        log.info("📥 Fetching products for userId={} with status={}", userId, status);

        Page<UserProduct> products =
                userProductRepository.findByUserIdAndApprovalStatus(userId, status, pageable);

        return products.map(this::mapToUserProductResponse);
    }


    public RequestProductResponse getUserProductRequestById(UUID requestId, UUID userId) {

        log.info("📥 Fetching product request id={} for userId={}", requestId, userId);

        ProductRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> {
                    log.error("❌ ProductRequest not found id={} for userId={}", requestId, userId);
                    return new RuntimeException("ProductRequest not found");
                });

        UserProduct product = userProductRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElse(null);

        return RequestProductResponse.builder()
                .userId(userId)
                .name(request.getUser().getFullName())
                .email(request.getUser().getEmail())
                .productResponse(mapToUserProductResponse(product))
                .payload(mapToPayload(request))
                .build();
    }


    public RequestProductResponse getProductRequestByIdForSeller(UUID requestId) {

        log.info("📥 Seller fetching product request id={}", requestId);

        ProductRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.error("❌ ProductRequest not found id={}", requestId);
                    return new RuntimeException("ProductRequest not found");
                });

        UserProduct product = userProductRepository
                .findTopByUserIdOrderByCreatedAtDesc(request.getUser().getId())
                .orElse(null);

        return RequestProductResponse.builder()
                .userId(request.getUser().getId())
                .name(request.getUser().getFullName())
                .email(request.getUser().getEmail())
                .productResponse(mapToUserProductResponse(product))
                .payload(mapToPayload(request))
                .build();
    }


    public Page<RequestProductResponse> getAllUserProductRequests(UUID userId, Pageable pageable) {

        log.info("📥 [UserAdvancedProductService] Fetching all product requests for userId={}", userId);

        Page<ProductRequest> requests = requestRepository.findByUserId(userId, pageable);

        Page<RequestProductResponse> responsePage = requests.map(request -> {
            UserProduct product = userProductRepository
                    .findTopByUserIdOrderByCreatedAtDesc(userId)
                    .orElse(null);

            return RequestProductResponse.builder()
                    .userId(userId)
                    .name(request.getUser().getFullName())
                    .email(request.getUser().getEmail())
                    .productResponse(mapToUserProductResponse(product))
                    .payload(mapToPayload(request))
                    .build();
        });

        log.info("✅ [UserAdvancedProductService] Retrieved {} product requests for userId={}",
                responsePage.getTotalElements(), userId);

        return responsePage;
    }


    // users to update the userproducts — they get 3 chances
    public UserProductResponse updateUserProduct(
            UUID productId,
            UUID userId,
            List<MultipartFile> newImages,
            UpdateUserProductRequest request
    ) throws IOException {

        userRepo.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ [UserAdvancedProductService] User not found — with id {}", userId);
                    return new RuntimeException("User not found");
                });

        UserProduct product = userProductRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> {
                    log.error("❌ [UserAdvancedProductService] Product not found — productId={} userId={}",
                            productId, userId);
                    return new RuntimeException("Product not found with id: " + productId);
                });

        // enforce the 3-update limit
        if (product.getUpdateCount() >= 3) {
            log.warn("⚠️ [updateUserProduct] Max updates reached for productId={}", productId);
            throw new RuntimeException("You have reached the maximum number of updates (3) for this product.");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName());
        }
        if (request.getProductDescription() != null && !request.getProductDescription().isBlank()) {
            product.setProductDescription(request.getProductDescription());
        }
        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            product.setBrand(request.getBrand());
        }

        // collect Cloudinary public-ids first, delete AFTER the DB save succeeds
        // so a DB rollback doesn't leave orphaned images in Cloudinary
        List<String> publicIdsToDelete = new ArrayList<>();

        if (request.getImageIdsToDelete() != null && !request.getImageIdsToDelete().isEmpty()) {
            for (Long imageId : request.getImageIdsToDelete()) {
                productImageRepository.findById(imageId).ifPresent(img -> {
                    publicIdsToDelete.add(img.getImagePublicId());
                    productImageRepository.delete(img);
                    log.info("Marked image [{}] for Cloudinary deletion from product [{}]", imageId, productId);
                });
            }
            product.getImages().removeIf(img -> request.getImageIdsToDelete().contains(img.getId()));
        }

        if (newImages != null && !newImages.isEmpty()) {
            int maxOrder = product.getImages().stream()
                    .mapToInt(UserProductImage::getDisplayOrder)
                    .max().orElse(-1);
            List<UserProductImage> added = uploadImages(newImages, product, maxOrder + 1);
            productImageRepository.saveAll(added);
            product.getImages().addAll(added);
            log.info("Added {} new image(s) to product [{}]", added.size(), product.getName());
        }

        // increment counter and save
        product.setUpdateCount(product.getUpdateCount() + 1);
        product.setUpdatedAt(LocalDateTime.now());

        UserProduct savedProduct = userProductRepository.save(product);

        // delete from Cloudinary only after DB save succeeds
        for (String publicId : publicIdsToDelete) {
            try {
                cloudinaryService.deleteImage(publicId);
                log.info("🗑️ Deleted image from Cloudinary: {}", publicId);
            } catch (IOException e) {
                log.error("⚠️ Cloudinary delete failed for [{}] — manual cleanup needed: {}", publicId, e.getMessage());
            }
        }

        return mapToUserProductResponse(savedProduct);
    }


    // admin/seller approving request products
    public AdminUserProductResponse adminUserProductUpdate(
            UUID sellerId,
            UUID productId,
            ApprovalStatus status
    ) {
        Seller seller = sellerRepo.findById(sellerId)
                .orElseThrow(() -> {
                    log.error("❌ [UserAdvancedProductService] Seller not found — id={}", sellerId);
                    return new RuntimeException("Seller not found with id: " + sellerId);
                });

        UserProduct product = userProductRepository.findById(productId)
                .orElseThrow(() -> {
                    log.error("❌ [UserAdvancedProductService] Product not found — id={}", productId);
                    return new RuntimeException("Product not found with id: " + productId);
                });

        product.setApprovalStatus(status);
        product.setUpdatedAt(LocalDateTime.now());

        UserProduct savedProduct = userProductRepository.save(product);

        log.info("✅ [UserAdvancedProductService] Product {} updated to status {}", productId, status);

        return AdminUserProductResponse.builder()
                .sellerId(seller.getId())
                .approvedAt(savedProduct.getUpdatedAt())
                .productResponse(mapToUserProductResponse(savedProduct))
                .build();
    }


    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private List<UserProductImage> uploadImages(
            List<MultipartFile> files,
            UserProduct product,
            int startOrder
    ) throws IOException {

        List<UserProductImage> result = new ArrayList<>();
        int order = startOrder;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            Map<String, Object> uploadResult =
                    cloudinaryService.uploadImage(file, "marketPlace/products");

            result.add(UserProductImage.builder()
                    .imageUrl((String) uploadResult.get("secure_url"))
                    .imagePublicId((String) uploadResult.get("public_id"))
                    .displayOrder(order++)
                    .product(product)
                    .build());
        }
        return result;
    }

    /**
     * Single source of truth for mapping UserProduct → UserProductResponse.
     * All methods (create, update, admin, list) funnel through here so
     * productDescription and updateCount are never accidentally omitted.
     */
    private UserProductResponse mapToUserProductResponse(UserProduct product) {
        if (product == null) return null;

        return UserProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .brand(product.getBrand())
                .productDescription(product.getProductDescription())
                .approvalStatus(product.getApprovalStatus())
                .updateCount(product.getUpdateCount())
                .images(
                        product.getImages() == null ? List.of() :
                                product.getImages().stream()
                                        .map(img -> ProductImageResponse.builder()
                                                .id(img.getId())
                                                .imageUrl(img.getImageUrl())
                                                .imagePublicId(img.getImagePublicId())
                                                .displayOrder(img.getDisplayOrder())
                                                .build())
                                        .toList()
                )
                .createdAt(product.getCreatedAt())
                .build();
    }


    private ProductRequestPayload mapToPayload(ProductRequest request) {
        if (request == null) return null;

        return ProductRequestPayload.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .userEmail(request.getUser().getEmail())
                .amount(request.getAmount())
                .paid(request.getPaid())
                // MoMo sender details
                .senderAccountName(request.getSenderAccountName())
                .senderPhoneNumber(request.getSenderPhoneNumber())
                .screenshotUrl(request.getScreenshotUrl())
                // Status
                .approvalStatus(request.getApprovalStatus())
                .hasProduct(request.getUserProduct() != null)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}