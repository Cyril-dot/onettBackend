package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal; // 👉 rename to SellerPrincipal later
import com.marketPlace.MarketPlace.Service.SellerRegistrationService;
import com.marketPlace.MarketPlace.dtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerRegistrationService sellerRegistrationService;

    // ═══════════════════════════════════════════════════════════
    // REGISTER
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SellerRegistrationResponse> register(
            @RequestPart("data") SellerRegistrationRequest request,
            @RequestPart("profilePic") MultipartFile profilePic
    ) throws IOException {

        SellerRegistrationResponse response =
                sellerRegistrationService.registerSeller(request, profilePic);

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/login")
    public ResponseEntity<SellerLoginResponse> login(
            @RequestBody SellerLoginRequest request
    ) throws IOException {

        SellerLoginResponse response =
                sellerRegistrationService.login(request);

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // GET MY PROFILE (SECURE)
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/me")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<SellerDetailsResponse> getMyProfile(
            @AuthenticationPrincipal AdminPrincipal principal // 👉 SellerPrincipal later
    ) {

        UUID sellerId = principal.getSellerId();

        SellerDetailsResponse response =
                sellerRegistrationService.viewSellerDetails(sellerId);

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // PUBLIC PROFILE (BUYERS VIEW STORE)
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/{sellerId}")
    public ResponseEntity<SellerDetailsResponse> getSellerById(
            @PathVariable UUID sellerId
    ) {

        SellerDetailsResponse response =
                sellerRegistrationService.viewSellerDetails(sellerId);

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE PROFILE (SECURE)
    // ═══════════════════════════════════════════════════════════

    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<SellerUpdateResponse> updateProfile(
            @AuthenticationPrincipal AdminPrincipal principal, // 👉 SellerPrincipal later
            @RequestPart("data") SellerUpdateRequest request,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        SellerUpdateResponse response =
                sellerRegistrationService.updateSeller(request, profilePic, sellerId);

        return ResponseEntity.ok(response);
    }
}