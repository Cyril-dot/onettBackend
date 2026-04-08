package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.PaystackInitiatePayload;
import com.marketPlace.MarketPlace.dtos.PaystackVerifyPayload;
import com.marketPlace.MarketPlace.entity.ProductRequest;
import com.marketPlace.MarketPlace.entity.Repo.ProductRequestRepository;
import com.marketPlace.MarketPlace.entity.Repo.UserRepo;
import com.marketPlace.MarketPlace.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaystackService {

    private final ProductRequestRepository productRequestRepository;
    private final UserRepo userRepository;
    private final RestTemplate restTemplate;

    @Value("${paystack.secret.key}")
    private String paystackSecretKey;

    @Value("${paystack.base.url}")
    private String paystackBaseUrl;

    @Value("${paystack.callback.url}")
    private String callbackUrl;


    // ════════════════════════════════════════════════════════════════
    // STEP 1 — User initiates payment to request a product listing
    // ════════════════════════════════════════════════════════════════

    public PaystackInitiatePayload initiatePayment(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // flat fee — always 100 GHS
        BigDecimal amount = BigDecimal.valueOf(100.00);
        int amountInPesewas = 10000; // Paystack uses smallest unit (pesewas for GHS)

        String reference = "MKT_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16).toUpperCase();

        Map<String, Object> paystackBody = new HashMap<>();
        paystackBody.put("email",        user.getEmail());
        paystackBody.put("amount",       amountInPesewas);
        paystackBody.put("currency",     "GHS");
        paystackBody.put("reference",    reference);
        paystackBody.put("callback_url", callbackUrl);
        paystackBody.put("metadata", Map.of(
                "userId",  user.getId().toString(),
                "purpose", "PRODUCT_LISTING"
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paystackBody, buildHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    paystackBaseUrl + "/transaction/initialize", entity, Map.class);

            Map responseBody = response.getBody();
            if (responseBody == null || !(Boolean) responseBody.get("status")) {
                throw new RuntimeException("Paystack initialization failed");
            }

            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            String authorizationUrl  = (String) data.get("authorization_url");
            String accessCode        = (String) data.get("access_code");

            // Save ProductRequest immediately as PENDING
            ProductRequest productRequest = ProductRequest.builder()
                    .user(user)
                    .amount(amount)
                    .paid(false)
                    .paystackReference(reference)
                    .build();

            productRequestRepository.save(productRequest);

            log.info("✅ Payment initiated — User: {}, Ref: {}, Amount: 100 GHS",
                    user.getEmail(), reference);

            return PaystackInitiatePayload.builder()
                    .reference(reference)
                    .authorizationUrl(authorizationUrl)
                    .accessCode(accessCode)
                    .amount(amount)
                    .currency("GHS")
                    .email(user.getEmail())
                    .build();

        } catch (Exception e) {
            log.error("❌ Paystack init failed: {}", e.getMessage());
            throw new RuntimeException("Payment initialization failed. Please try again.");
        }
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 2 — Verify payment (frontend redirect or webhook)
    // ════════════════════════════════════════════════════════════════

    public PaystackVerifyPayload verifyPayment(String reference) {

        // Idempotency — already verified, return early
        ProductRequest productRequest = productRequestRepository
                .findByPaystackReference(reference)
                .orElse(null);

        if (productRequest == null) {
            log.warn("⚠️ ProductRequest not found for ref: {} — may still be processing", reference);
            return PaystackVerifyPayload.builder()
                    .reference(reference)
                    .status("pending")
                    .message("Payment is still processing. Please wait and try again.")
                    .paid(false)
                    .build();
        }

        if (productRequest.getPaid()) {
            log.info("⏩ Payment {} already verified — returning cached result", reference);
            return PaystackVerifyPayload.builder()
                    .reference(reference)
                    .status("success")
                    .message("Payment already verified. You can now upload your product.")
                    .paid(true)
                    .productRequestId(productRequest.getId())
                    .build();
        }

        // Call Paystack to verify
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    paystackBaseUrl + "/transaction/verify/" + reference,
                    HttpMethod.GET, entity, Map.class);

            Map responseBody = response.getBody();
            if (responseBody == null || !(Boolean) responseBody.get("status")) {
                throw new RuntimeException("Paystack verification call failed");
            }

            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            String paystackStatus = (String) data.get("status");

            if ("success".equals(paystackStatus)) {
                productRequest.setPaid(true);
                productRequestRepository.save(productRequest);

                log.info("✅ Payment verified — Ref: {}, User: {}",
                        reference, productRequest.getUser().getEmail());

                return PaystackVerifyPayload.builder()
                        .reference(reference)
                        .status("success")
                        .message("Payment successful! You can now upload your product.")
                        .paid(true)
                        .productRequestId(productRequest.getId())
                        .build();

            } else {
                log.warn("❌ Payment failed — Ref: {}, Status: {}", reference, paystackStatus);
                return PaystackVerifyPayload.builder()
                        .reference(reference)
                        .status("failed")
                        .message("Payment failed. Please try again.")
                        .paid(false)
                        .build();
            }

        } catch (Exception e) {
            log.error("❌ Verification error for ref {}: {}", reference, e.getMessage());
            throw new RuntimeException("Payment verification failed. Contact support.");
        }
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 3 — Paystack Webhook (charge.success)
    // ════════════════════════════════════════════════════════════════

    public void handleWebhook(Map<String, Object> payload, String rawBody, String signature) {
        if (!isValidSignature(rawBody, signature)) {
            log.warn("⚠️ Invalid Paystack webhook signature — rejecting");
            throw new RuntimeException("Invalid webhook signature");
        }

        String event = (String) payload.get("event");
        log.info("📩 Webhook received: {}", event);

        if ("charge.success".equals(event)) {
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String reference = (String) data.get("reference");

            // Skip if already processed
            productRequestRepository.findByPaystackReference(reference)
                    .filter(ProductRequest::getPaid)
                    .ifPresent(pr -> {
                        log.info("⏩ Webhook: {} already processed — skipping", reference);
                        return;
                    });

            try {
                verifyPayment(reference);
                log.info("✅ Webhook processed for ref: {}", reference);
            } catch (Exception e) {
                log.error("❌ Webhook processing failed for ref {}: {}", reference, e.getMessage());
            }
        }
    }


    // ════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(paystackSecretKey);
        return headers;
    }

    private boolean isValidSignature(String rawBody, String signature) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    paystackSecretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "HmacSHA512"));

            byte[] hashBytes = mac.doFinal(
                    rawBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hexHash = new StringBuilder();
            for (byte b : hashBytes) hexHash.append(String.format("%02x", b));

            boolean valid = hexHash.toString().equals(signature);
            if (!valid) log.warn("⚠️ HMAC mismatch — computed: {}, received: {}", hexHash, signature);
            return valid;

        } catch (Exception e) {
            log.error("❌ Signature validation error: {}", e.getMessage());
            return false;
        }
    }
}