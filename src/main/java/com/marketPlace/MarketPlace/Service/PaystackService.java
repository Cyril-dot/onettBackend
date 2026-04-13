package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.ProductListingPaymentResponse;
import com.marketPlace.MarketPlace.dtos.ProductRequestAdminResponse;
import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import com.marketPlace.MarketPlace.entity.ProductRequest;
import com.marketPlace.MarketPlace.entity.Repo.ProductRequestRepository;
import com.marketPlace.MarketPlace.entity.Repo.UserRepo;
import com.marketPlace.MarketPlace.entity.User;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaystackService {

    private final ProductRequestRepository productRequestRepository;
    private final UserRepo                 userRepository;
    private final CloudinaryService        cloudinaryService;
    private final NotificationService      notificationService;

    private static final BigDecimal LISTING_FEE = BigDecimal.valueOf(100.00);

    // ════════════════════════════════════════════════════════════════
    // STEP 1 — User submits MoMo payment proof for product listing fee
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public ProductListingPaymentResponse submitListingPayment(UUID userId,
                                                              String senderAccountName,
                                                              String senderPhoneNumber,
                                                              MultipartFile screenshot) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // ── Upload screenshot to Cloudinary ───────────────────────────────────
        String screenshotUrl;
        String screenshotPublicId;
        try {
            Map uploadResult   = cloudinaryService.uploadPaymentScreenshot(screenshot);
            screenshotUrl      = (String) uploadResult.get("secure_url");
            screenshotPublicId = (String) uploadResult.get("public_id");
        } catch (Exception e) {
            log.error("Cloudinary upload failed for user [{}]: {}", userId, e.getMessage());
            throw new ApiException("Screenshot upload failed — please try again");
        }

        // ── Save ProductRequest as unpaid (pending admin confirmation) ─────────
        ProductRequest productRequest = ProductRequest.builder()
                .user(user)
                .amount(LISTING_FEE)
                .paid(false)
                .approvalStatus(ApprovalStatus.PENDING)
                .senderAccountName(senderAccountName)
                .senderPhoneNumber(senderPhoneNumber)
                .screenshotUrl(screenshotUrl)
                .screenshotPublicId(screenshotPublicId)
                .build();

        productRequestRepository.save(productRequest);

        // ── Notify user (submitted) + notify all admins ────────────────────────
        notificationService.notifyProductRequestSubmitted(user, productRequest.getId());
        notificationService.notifyAdminNewListingPaymentSubmitted(
                user, productRequest.getId(), senderAccountName, senderPhoneNumber);

        log.info("Listing payment submitted — User: {}, Sender: {}, Amount: 100 GHS",
                user.getEmail(), senderAccountName);

        return ProductListingPaymentResponse.builder()
                .productRequestId(productRequest.getId())
                .screenshotUrl(screenshotUrl)
                .amount(LISTING_FEE)
                .message("Payment submitted. Awaiting admin confirmation before you can upload your product.")
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // STEP 2 — Admin confirms listing payment
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public void confirmListingPayment(UUID productRequestId) {
        ProductRequest productRequest = productRequestRepository.findById(productRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProductRequest not found: " + productRequestId));

        if (productRequest.getPaid()) {
            log.info("ProductRequest [{}] already confirmed — skipping", productRequestId);
            return;
        }

        productRequest.setPaid(true);
        productRequest.setApprovalStatus(ApprovalStatus.APPROVED);
        productRequestRepository.save(productRequest);

        // ── Notify user their request was approved ─────────────────────────────
        notificationService.notifyProductRequestApproved(
                productRequest.getUser(), productRequestId);

        log.info("Listing payment CONFIRMED for ProductRequest [{}] — User: {}",
                productRequestId, productRequest.getUser().getEmail());
    }

    // ════════════════════════════════════════════════════════════════
    // STEP 3 — Admin rejects listing payment
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public void rejectListingPayment(UUID productRequestId, String reason) {
        ProductRequest productRequest = productRequestRepository.findById(productRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProductRequest not found: " + productRequestId));

        productRequest.setPaid(false);
        productRequest.setApprovalStatus(ApprovalStatus.REJECTED);
        productRequest.setAdminNote(reason);
        productRequestRepository.save(productRequest);

        // ── Notify user their request was rejected ─────────────────────────────
        notificationService.notifyProductRequestRejected(
                productRequest.getUser(), productRequestId, reason);

        // ── Notify admins for audit trail ──────────────────────────────────────
        notificationService.notifyAdminListingPaymentRejected(
                productRequest.getUser(), productRequestId, reason);

        log.warn("Listing payment REJECTED for ProductRequest [{}] — Reason: {}",
                productRequestId, reason);
    }

    // ════════════════════════════════════════════════════════════════
    // ADMIN — View all payment requests (paginated)
    // ════════════════════════════════════════════════════════════════

    public Page<ProductRequestAdminResponse> getAllPaymentRequests(Pageable pageable) {
        return productRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToAdminResponse);
    }

    // ════════════════════════════════════════════════════════════════
    // ADMIN — View requests filtered by approval status
    // ════════════════════════════════════════════════════════════════

    public Page<ProductRequestAdminResponse> getPaymentRequestsByStatus(ApprovalStatus status,
                                                                        Pageable pageable) {
        return productRequestRepository.findByApprovalStatus(status, pageable)
                .map(this::mapToAdminResponse);
    }

    // ════════════════════════════════════════════════════════════════
    // ADMIN — View single payment request by ID
    // ════════════════════════════════════════════════════════════════

    public ProductRequestAdminResponse getPaymentRequestById(UUID productRequestId) {
        ProductRequest productRequest = productRequestRepository.findById(productRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProductRequest not found: " + productRequestId));
        return mapToAdminResponse(productRequest);
    }

    // ════════════════════════════════════════════════════════════════
    // ADMIN — Dashboard counts by status
    // ════════════════════════════════════════════════════════════════

    public Map<String, Long> getPaymentRequestCounts() {
        return Map.of(
                "pending",  productRequestRepository.countByApprovalStatus(ApprovalStatus.PENDING),
                "approved", productRequestRepository.countByApprovalStatus(ApprovalStatus.APPROVED),
                "rejected", productRequestRepository.countByApprovalStatus(ApprovalStatus.REJECTED)
        );
    }

    // ════════════════════════════════════════════════════════════════
    // PRIVATE — Mapper
    // ════════════════════════════════════════════════════════════════

    private ProductRequestAdminResponse mapToAdminResponse(ProductRequest pr) {
        return ProductRequestAdminResponse.builder()
                .id(pr.getId())
                .userId(pr.getUser().getId())
                .userFullName(pr.getUser().getFullName())
                .userEmail(pr.getUser().getEmail())
                .amount(pr.getAmount())
                .paid(pr.getPaid())
                .approvalStatus(pr.getApprovalStatus())
                .senderAccountName(pr.getSenderAccountName())
                .senderPhoneNumber(pr.getSenderPhoneNumber())
                .screenshotUrl(pr.getScreenshotUrl())
                .adminNote(pr.getAdminNote())
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .build();
    }
}