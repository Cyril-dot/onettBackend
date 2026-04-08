package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.UserRegistrationService;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRegistrationService userRegistrationService;

    // ═══════════════════════════════════════════════════════════
    // REGISTER
    // POST /api/v1/users/register
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserRegistrationResponse> register(
            @RequestPart("data") UserRegistrationRequest request,
            @RequestPart("profilePic") MultipartFile profilePic
    ) throws IOException {
        UserRegistrationResponse response = userRegistrationService.registerUser(request, profilePic);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // LOGIN
    // POST /api/v1/users/login
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(
            @RequestBody UserLoginRequest request
    ) {
        UserLoginResponse response = userRegistrationService.userLogin(request);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // VIEW OWN PROFILE
    // GET /api/v1/users/me
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserDetailsResponse> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal.getUserId();
        String email = principal.getEmail();
        UserDetailsResponse response = userRegistrationService.viewUserDetails(userId, email);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // VIEW PROFILE PIC
    // GET /api/v1/users/me/profile-pic
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/me/profile-pic")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ProfilePicResponse> getProfilePic(
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        UUID userId = principal.getUserId();
        ProfilePicResponse response = userRegistrationService.viewProfilePic(userId);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE PROFILE
    // PUT /api/v1/users/me
    // ═══════════════════════════════════════════════════════════

    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserUpdateResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("data") UserUpdateRequest request,
            @RequestPart("profilePic") MultipartFile profilePic
    ) throws IOException {
        UUID userId = principal.getUserId();
        UserUpdateResponse response = userRegistrationService.updateUserDetails(request, profilePic, userId);
        return ResponseEntity.ok(response);
    }
}