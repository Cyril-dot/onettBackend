package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal;
import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.PreOrderService;
import com.marketPlace.MarketPlace.dtos.PreOrderRecordResponse;
import com.marketPlace.MarketPlace.entity.Enums.PreOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pre-orders")
@RequiredArgsConstructor
public class PreOrderController {

    private final PreOrderService preOrderService;

    // ─── USER ─────────────────────────────────────────────────────────────

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PreOrderRecordResponse>> getMyPreOrders(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();

        return ResponseEntity.ok(preOrderService.getUserPreOrders(userId));
    }

    @PostMapping("/{preOrderRecordId}/request-delivery")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PreOrderRecordResponse> requestDelivery(
            @PathVariable UUID preOrderRecordId,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();

        return ResponseEntity.ok(
                preOrderService.requestDelivery(preOrderRecordId, userId)
        );
    }

    // ─── SELLER (NOT ADMIN) ───────────────────────────────────────────────

    @GetMapping("/seller/all")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<PreOrderRecordResponse>> getAllActivePreOrders() {
        return ResponseEntity.ok(preOrderService.getAllActivePreOrders());
    }

    @GetMapping("/seller/product/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<PreOrderRecordResponse>> getPreOrdersByProduct(
            @PathVariable UUID productId) {

        return ResponseEntity.ok(
                preOrderService.getPreOrdersByProduct(productId)
        );
    }

    @GetMapping("/seller/status/{status}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<PreOrderRecordResponse>> getPreOrdersByStatus(
            @PathVariable PreOrderStatus status) {

        return ResponseEntity.ok(
                preOrderService.getPreOrdersByStatus(status)
        );
    }

    @PostMapping("/seller/{preOrderRecordId}/confirm-payment")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<PreOrderRecordResponse> confirmSecondPayment(
            @PathVariable UUID preOrderRecordId,
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestBody(required = false) Map<String, String> body) {

        UUID sellerId = principal.getSellerId(); // ✅ correct

        String note = body != null ? body.get("adminNote") : null;

        return ResponseEntity.ok(
                preOrderService.confirmSecondPayment(preOrderRecordId, sellerId, note)
        );
    }
}