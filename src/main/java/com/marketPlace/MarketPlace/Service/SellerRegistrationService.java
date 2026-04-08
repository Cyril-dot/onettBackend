package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.Config.Security.TokenService;
import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.ProfilePic;
import com.marketPlace.MarketPlace.entity.Repo.ProfilePicImageRepo;
import com.marketPlace.MarketPlace.entity.Repo.SellerRepo;
import com.marketPlace.MarketPlace.entity.Seller;
import com.marketPlace.MarketPlace.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerRegistrationService {

    private final SellerRepo sellerRepo;
    private final ProfilePicImageRepo profilePicImageRepo;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private final TokenService tokenService;


    public SellerRegistrationResponse registerSeller(SellerRegistrationRequest request, MultipartFile profilePic) throws IOException {
        log.info("Validating seller registration for email: {}", request.getEmail());

        if (isEmailTaken(request.getEmail())) {
            log.error("Seller registration failed — email already exists: {}", request.getEmail());
            throw new RuntimeException("Email " + request.getEmail() + " is already registered");
        }
        if (!isValidAndAvailablePhoneNumber(request.getPhoneNumber())) {
            log.error("Seller registration failed — invalid or taken phone number: {}", request.getPhoneNumber());
            throw new RuntimeException("Phone number is invalid or already registered");
        }
        if (isStoreNameTaken(request.getStoreName())) {
            log.error("Seller registration failed — store name already exists: {}", request.getStoreName());
            throw new RuntimeException("Store name " + request.getStoreName() + " is already taken");
        }
        if (isTaxIdTaken(request.getTaxId())) {
            log.error("Seller registration failed — tax ID already exists: {}", request.getTaxId());
            throw new RuntimeException("Tax ID " + request.getTaxId() + " is already registered");
        }

        log.info("Seller registration validation passed for email: {}", request.getEmail());
        log.info("Seller registration Begins");

        Seller seller = new Seller();
        seller.setFullName(request.getFullName());
        seller.setEmail(request.getEmail());
        seller.setPhoneNumber(request.getPhoneNumber());
        seller.setLocation(request.getLocation());
        seller.setPassword(passwordEncoder.encode(request.getPassword()));
        seller.setStoreName(request.getStoreName());
        seller.setTaxId(request.getTaxId());
        seller.setStoreDescription(request.getStoreDescription());
        seller.setSellerBio(request.getSellerBio());
        seller.setBusinessAddress(request.getBusinessAddress());
        Seller savedSeller =  sellerRepo.save(seller);

        ProfilePic pic = uploadProfilePic(profilePic, savedSeller);
        savedSeller.setProfilePic(pic);
        savedSeller.setCreatedAt(LocalDateTime.now());
        sellerRepo.save(savedSeller);


        return SellerRegistrationResponse.builder()
                .id(savedSeller.getId())
                .fullName(savedSeller.getFullName())
                .email(savedSeller.getEmail())
                .role(savedSeller.getRole().name())
                .phoneNumber(savedSeller.getPhoneNumber())
                .storeName(savedSeller.getStoreName())
                .businessAddress(savedSeller.getBusinessAddress())
                .createdAt(savedSeller.getCreatedAt())
                .build();

    }


    // to update seller detils
    public SellerUpdateResponse updateSeller(SellerUpdateRequest request, MultipartFile profilePic, UUID sellerId) throws IOException {
        Seller seller = sellerRepo.findById(sellerId).orElseThrow(() -> new RuntimeException("Seller not found"));

        if (request.getFullName() != null || !request.getFullName().isEmpty()) {
            seller.setFullName(request.getFullName());
        }

        if (request.getPhoneNumber() != null || !request.getPhoneNumber().isEmpty()) {
            seller.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getLocation() != null || !request.getLocation().isEmpty()) {
            seller.setLocation(request.getLocation());
        }

        if (request.getSellerBio() != null || !request.getSellerBio().isEmpty()) {
            seller.setSellerBio(request.getSellerBio());
        }

        if (request.getStoreName() != null  || request.getStoreName().isEmpty()) {
            seller.setStoreName(request.getStoreName());
        }
        if (request.getBusinessAddress() != null || !request.getBusinessAddress().isEmpty()) {
            seller.setBusinessAddress(request.getBusinessAddress());
        }
        if (request.getStoreDescription()  != null || !request.getStoreDescription().isEmpty()) {
            seller.setStoreDescription(request.getStoreDescription());
        }

        seller.setUpdatedAt(LocalDateTime.now());
        ProfilePic pic = uploadProfilePic(profilePic, seller);
        seller.setProfilePic(pic);
        sellerRepo.save(seller);

        return SellerUpdateResponse.builder()
                .id(seller.getId())
                .fullName(seller.getFullName())
                .email(seller.getEmail())
                .phoneNumber(seller.getPhoneNumber())
                .storeName(seller.getStoreName())
                .location(seller.getLocation())
                .businessAddress(seller.getBusinessAddress())
                .sellerBio(seller.getSellerBio())
                .storeDescription(seller.getStoreDescription())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // to login
    public SellerLoginResponse login(SellerLoginRequest request) throws IOException {
        Seller seller = sellerRepo.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("Seller not found"));

        if (!passwordEncoder.matches(request.getPassword(), seller.getPassword())) {
            log.error("Passwords don't match");
            throw new IOException("Passwords don't match");
        }

        String accessToken = tokenService.generateOwnerAccessToken(seller);
        String refreshToken = tokenService.generateAdminRefreshToken(seller).getToken();

        return SellerLoginResponse.builder()
                .id(seller.getId())
                .fullName(seller.getFullName())
                .email(seller.getEmail())
                .storeName(seller.getStoreName())
                .role(seller.getRole())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // to view seller details
    public SellerDetailsResponse viewSellerDetails(UUID sellerId) {
         Seller seller = sellerRepo.findById(sellerId)
                 .orElseThrow(() -> new RuntimeException("Seller not found"));

        return SellerDetailsResponse.builder()
                .id(seller.getId())
                .fullName(seller.getFullName())
                .email(seller.getEmail())
                .phoneNumber(seller.getPhoneNumber())
                .location(seller.getLocation())
                .sellerBio(seller.getSellerBio())
                .storeName(seller.getStoreName())
                .storeDescription(seller.getStoreDescription())
                .businessAddress(seller.getBusinessAddress())
                .taxId(seller.getTaxId())
                .isVerified(seller.isVerified())
                .role(seller.getRole().name())
                .createdAt(seller.getCreatedAt())
                .updatedAt(seller.getUpdatedAt())
                .profilePic(seller.getProfilePic() != null ? ProfilePicResponse.builder()
                        .id(seller.getProfilePic().getId())
                        .imageUrl(seller.getProfilePic().getImageUrl())
                        .imagePublicId(seller.getProfilePic().getImagePublicId())
                        .build() : null)
                .build();

    }


    /** Upload a list of files starting at a given displayOrder offset */
    private ProfilePic uploadProfilePic(MultipartFile profilePic, Seller user) throws IOException {

        if (profilePic == null || profilePic.isEmpty()) {
            throw new IllegalArgumentException("Profile picture cannot be empty");
        }

        // Delete previous image from Cloudinary + DB if it exists
        if (user.getProfilePic() != null) {
            cloudinaryService.deleteImage(user.getProfilePic().getImagePublicId());
            profilePicImageRepo.delete(user.getProfilePic());
        }

        Map uploadResult = cloudinaryService.uploadImage(profilePic, "marketPlace/profile_pics");

        ProfilePic pic = ProfilePic.builder()
                .imageUrl((String) uploadResult.get("secure_url"))
                .imagePublicId((String) uploadResult.get("public_id"))
                .build();

        return profilePicImageRepo.save(pic);
    }



    private boolean isEmailTaken(String email) {
        boolean exists = sellerRepo.existsByEmail(email);
        if (exists) log.warn("Registration attempt with existing email: {}", email);
        return exists;
    }

    private boolean isValidAndAvailablePhoneNumber(String phoneNumber) {
        if (!UserRegistrationService.isValidGhanaPhoneNumber(phoneNumber)) {
            log.warn("Invalid Ghana phone number format: {}", phoneNumber);
            return false;
        }
        boolean exists = sellerRepo.existsByPhoneNumber(phoneNumber);
        if (exists) log.warn("Registration attempt with existing phone number: {}", phoneNumber);
        return !exists;
    }

    private boolean isStoreNameTaken(String storeName) {
        boolean exists = sellerRepo.existsByStoreName(storeName);
        if (exists) log.warn("Registration attempt with existing store name: {}", storeName);
        return exists;
    }

    private boolean isTaxIdTaken(String taxId) {
        boolean exists = sellerRepo.existsByTaxId(taxId);
        if (exists) log.warn("Registration attempt with existing tax ID: {}", taxId);
        return exists;
    }

}
