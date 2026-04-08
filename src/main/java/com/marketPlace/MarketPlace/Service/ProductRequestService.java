package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.PaystackInitiatePayload;
import com.marketPlace.MarketPlace.dtos.PaystackVerifyPayload;
import com.marketPlace.MarketPlace.dtos.ProductRequestInitiatePayload;
import com.marketPlace.MarketPlace.dtos.ProductRequestVerifyPayload;
import com.marketPlace.MarketPlace.dtos.ProductRequestPayload;
import com.marketPlace.MarketPlace.entity.ProductRequest;
import com.marketPlace.MarketPlace.entity.Repo.ProductRequestRepository;
import com.marketPlace.MarketPlace.entity.Repo.UserRepo;
import com.marketPlace.MarketPlace.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRequestService {

    private final ProductRequestRepository productRequestRepository;
    private final UserRepo userRepository;
    private final PaystackService paystackService;


    // ════════════════════════════════════════════════════════════════
    // STEP 1 — User initiates payment for a product listing
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public ProductRequestInitiatePayload initiatePayment(UUID userId) {
        log.info("🚀 [ProductRequestService] Initiating payment for userId: {}", userId);

        // 1. Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ [ProductRequestService] User not found — userId: {}", userId);
                    return new RuntimeException("User not found");
                });

        log.debug("✅ [ProductRequestService] User found — email: {}", user.getEmail());

        try {
            // 2. Delegate to PaystackService — handles ProductRequest creation + Paystack call
            PaystackInitiatePayload paystackResponse = paystackService.initiatePayment(userId);

            log.info("✅ [ProductRequestService] Payment initiated successfully — " +
                            "userId: {}, email: {}, reference: {}, amount: {} GHS",
                    userId, user.getEmail(),
                    paystackResponse.getReference(),
                    paystackResponse.getAmount());

            // 3. Map Paystack response to our own response DTO
            return ProductRequestInitiatePayload.builder()
                    .productRequestId(paystackResponse.getProductRequestId())
                    .reference(paystackResponse.getReference())
                    .authorizationUrl(paystackResponse.getAuthorizationUrl())
                    .accessCode(paystackResponse.getAccessCode())
                    .amount(paystackResponse.getAmount())
                    .currency(paystackResponse.getCurrency())
                    .email(paystackResponse.getEmail())
                    .build();

        } catch (RuntimeException e) {
            log.error("❌ [ProductRequestService] Payment initiation failed — " +
                    "userId: {}, reason: {}", userId, e.getMessage());
            throw e;
        }
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 2 — Verify payment after Paystack redirects user back
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public ProductRequestVerifyPayload verifyPayment(String reference) {
        log.info("🔍 [ProductRequestService] Verifying payment — reference: {}", reference);

        if (reference == null || reference.isBlank()) {
            log.error("❌ [ProductRequestService] Payment reference is null or blank");
            throw new RuntimeException("Payment reference cannot be empty");
        }

        try {
            // 1. Delegate to PaystackService — handles paid = true update
            PaystackVerifyPayload paystackResponse = paystackService.verifyPayment(reference);

            log.info("✅ [ProductRequestService] Payment verification completed — " +
                            "reference: {}, status: {}, paid: {}",
                    reference,
                    paystackResponse.getStatus(),
                    paystackResponse.getPaid());

            // 2. Map to our own response DTO
            return ProductRequestVerifyPayload.builder()
                    .productRequestId(paystackResponse.getProductRequestId())
                    .reference(paystackResponse.getReference())
                    .status(paystackResponse.getStatus())
                    .message(paystackResponse.getMessage())
                    .paid(paystackResponse.getPaid())
                    .build();

        } catch (RuntimeException e) {
            log.error("❌ [ProductRequestService] Payment verification failed — " +
                    "reference: {}, reason: {}", reference, e.getMessage());
            throw e;
        }
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 3 — Get a specific ProductRequest (before uploading product)
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
                    return new RuntimeException("Product request not found");
                });

        // 2. Ownership check
        if (!productRequest.getUser().getId().equals(userId)) {
            log.warn("⚠️ [ProductRequestService] Unauthorized access attempt — " +
                            "productRequestId: {}, requestingUserId: {}, ownerUserId: {}",
                    productRequestId, userId, productRequest.getUser().getId());
            throw new RuntimeException("Unauthorized — this product request does not belong to you");
        }

        // 3. Check if payment was completed
        if (!productRequest.getPaid()) {
            log.warn("⚠️ [ProductRequestService] Attempt to access unpaid ProductRequest — " +
                    "productRequestId: {}, userId: {}", productRequestId, userId);
            throw new RuntimeException("Payment not completed for this product request");
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
                .paystackReference(pr.getPaystackReference())
                .approvalStatus(pr.getApprovalStatus())
                .hasProduct(pr.getUserProduct() != null)
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .build();
    }
}