package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.ProductListingPaymentResponse;
import com.marketPlace.MarketPlace.dtos.ProductRequestPayload;
import com.marketPlace.MarketPlace.entity.ProductRequest;
import com.marketPlace.MarketPlace.entity.Repo.ProductRequestRepository;
import com.marketPlace.MarketPlace.entity.Repo.UserRepo;
import com.marketPlace.MarketPlace.entity.User;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRequestService {

    private final ProductRequestRepository productRequestRepository;
    private final UserRepo                 userRepository;
    private final PaystackService          paystackService;
    private final NotificationService      notificationService;


    // ════════════════════════════════════════════════════════════════
    // STEP 1 — User submits MoMo payment proof for a product listing
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public ProductListingPaymentResponse submitListingPayment(UUID userId,
                                                              String senderAccountName,
                                                              String senderPhoneNumber,
                                                              MultipartFile screenshot) {
        log.info("🚀 [ProductRequestService] Submitting listing payment — userId: {}", userId);

        // Validate user exists before delegating
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ [ProductRequestService] User not found — userId: {}", userId);
                    return new ResourceNotFoundException("User not found: " + userId);
                });

        log.debug("✅ [ProductRequestService] User found — email: {}", user.getEmail());

        try {
            // Delegate to PaystackService — handles Cloudinary upload + ProductRequest creation
            ProductListingPaymentResponse response = paystackService.submitListingPayment(
                    userId, senderAccountName, senderPhoneNumber, screenshot);

            log.info("✅ [ProductRequestService] Listing payment submitted — " +
                            "userId: {}, email: {}, productRequestId: {}",
                    userId, user.getEmail(), response.getProductRequestId());

            // Notify user that their submission is under review
            notificationService.notifyProductRequestSubmitted(user, response.getProductRequestId());

            return response;

        } catch (ApiException | ResourceNotFoundException e) {
            log.error("❌ [ProductRequestService] Listing payment submission failed — " +
                    "userId: {}, reason: {}", userId, e.getMessage());
            throw e;
        }
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 2 — Admin confirms MoMo payment (marks paid = true)
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public void confirmListingPayment(UUID productRequestId) {
        log.info("✅ [ProductRequestService] Admin confirming payment — productRequestId: {}",
                productRequestId);

        // Fetch before confirming so we can notify the correct user
        ProductRequest productRequest = productRequestRepository.findById(productRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProductRequest not found: " + productRequestId));

        User user = productRequest.getUser();

        // Delegate state change to PaystackService
        paystackService.confirmListingPayment(productRequestId);

        log.info("✅ [ProductRequestService] Payment confirmed — productRequestId: {}, user: {}",
                productRequestId, user.getEmail());

        // Notify user their payment was confirmed and they can now upload their product
        notificationService.notifyProductRequestApproved(user, productRequestId);
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 3 — Admin rejects MoMo payment
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public void rejectListingPayment(UUID productRequestId, String reason) {
        log.warn("⚠️ [ProductRequestService] Admin rejecting payment — " +
                "productRequestId: {}, reason: {}", productRequestId, reason);

        // Fetch before rejecting so we can notify the correct user
        ProductRequest productRequest = productRequestRepository.findById(productRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProductRequest not found: " + productRequestId));

        User user = productRequest.getUser();

        // Delegate state change to PaystackService
        paystackService.rejectListingPayment(productRequestId, reason);

        log.warn("❌ [ProductRequestService] Payment rejected — productRequestId: {}, user: {}",
                productRequestId, user.getEmail());

        // Notify user their payment was rejected with reason
        notificationService.notifyProductRequestRejected(user, productRequestId, reason);
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 4 — Get a specific ProductRequest (before uploading product)
    // ════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ProductRequestPayload getProductRequest(UUID productRequestId, UUID userId) {
        log.info("📋 [ProductRequestService] Fetching ProductRequest — " +
                "productRequestId: {}, userId: {}", productRequestId, userId);

        // 1. Find product request
        ProductRequest productRequest = productRequestRepository.findById(productRequestId)
                .orElseThrow(() -> {
                    log.error("❌ [ProductRequestService] ProductRequest not found — id: {}",
                            productRequestId);
                    return new ResourceNotFoundException("Product request not found: " + productRequestId);
                });

        // 2. Ownership check
        if (!productRequest.getUser().getId().equals(userId)) {
            log.warn("⚠️ [ProductRequestService] Unauthorized access — " +
                            "productRequestId: {}, requestingUserId: {}, ownerUserId: {}",
                    productRequestId, userId, productRequest.getUser().getId());
            throw new ApiException("Unauthorized — this product request does not belong to you");
        }

        // 3. Check if admin has confirmed payment
        if (!productRequest.getPaid()) {
            log.warn("⚠️ [ProductRequestService] Attempt to access unconfirmed ProductRequest — " +
                    "productRequestId: {}, userId: {}", productRequestId, userId);
            throw new ApiException("Payment not yet confirmed for this product request");
        }

        log.info("✅ [ProductRequestService] ProductRequest fetched — " +
                        "id: {}, paid: {}, hasProduct: {}",
                productRequestId,
                productRequest.getPaid(),
                productRequest.getUserProduct() != null);

        return mapToPayload(productRequest);
    }


    // ════════════════════════════════════════════════════════════════
    // PRIVATE HELPER — Map entity → Payload
    // ════════════════════════════════════════════════════════════════

    private ProductRequestPayload mapToPayload(ProductRequest pr) {
        return ProductRequestPayload.builder()
                .id(pr.getId())
                .userId(pr.getUser().getId())
                .userEmail(pr.getUser().getEmail())
                .amount(pr.getAmount())
                .paid(pr.getPaid())
                .senderAccountName(pr.getSenderAccountName())
                .senderPhoneNumber(pr.getSenderPhoneNumber())
                .screenshotUrl(pr.getScreenshotUrl())
                .approvalStatus(pr.getApprovalStatus())
                .hasProduct(pr.getUserProduct() != null)
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .build();
    }
}