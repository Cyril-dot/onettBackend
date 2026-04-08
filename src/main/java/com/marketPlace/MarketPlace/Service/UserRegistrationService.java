package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.Config.Security.TokenService;
import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.Enums.Role;
import com.marketPlace.MarketPlace.entity.ProfilePic;
import com.marketPlace.MarketPlace.entity.Repo.CategoryIconRepo;
import com.marketPlace.MarketPlace.entity.Repo.ProfilePicImageRepo;
import com.marketPlace.MarketPlace.entity.Repo.UserRepo;
import com.marketPlace.MarketPlace.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationService {

    private final UserRepo userRepo;
    private final ProfilePicImageRepo profilePicImageRepo;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private final TokenService tokenService;

    public UserRegistrationResponse registerUser(UserRegistrationRequest request, MultipartFile profilePicImage) throws IOException {
        // now to check if user exists
        // AFTER (correct):
        if (userAlreadyExists(request.getEmail())) {
            log.error("User with email {} already exists", request.getEmail());
            throw new RuntimeException("User with email " + request.getEmail() + " already exists");
        }

        if (!isValidGhanaPhoneNumber(request.getPhoneNumber())) {
            log.error("Invalid phone number format");
            throw new RuntimeException("Invalid phone number: " + request.getPhoneNumber());
        }

        if (phoneNumberAlreadyRegistered(request.getPhoneNumber())) {
            log.error("User with phone number {} already exists", request.getPhoneNumber());
            throw new RuntimeException("User with phone number " + request.getPhoneNumber() + " already exists");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setLocation(request.getLocation());
        user.setCreatedAt(LocalDateTime.now());
        user.setBio(request.getBio());

        User savedUser = userRepo.save(user);
        ProfilePic pic = uploadProfilePic(profilePicImage, savedUser);
        savedUser.setProfilePic(pic);
        userRepo.save(savedUser);

        return UserRegistrationResponse.builder()
                .id(savedUser.getId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .phoneNumber(savedUser.getPhoneNumber())
                .location(savedUser.getLocation())
                .role(Role.USER)
                .build();
    }

    public UserUpdateResponse updateUserDetails(UserUpdateRequest request, MultipartFile profilePicImage, UUID id) throws IOException {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User with id " + id + " does not exist"));

        if(request.getFullName() != null && !request.getFullName().isEmpty()) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isEmpty()) {
            if(isValidGhanaPhoneNumber(request.getPhoneNumber())) {
                user.setPhoneNumber(request.getPhoneNumber());
            }else {
                log.error("Invalid phone number");
                throw new RuntimeException("Invalid phone number");
            }
        }

        if (request.getLocation() != null && !request.getLocation().isEmpty()) {
            user.setLocation(request.getLocation());
        }

        if (request.getBio() != null && !request.getBio().isEmpty() ) {
            user.setBio(request.getBio());
        }
        User savedUser = userRepo.save(user);
        ProfilePic pic = uploadProfilePic(profilePicImage, savedUser);
        savedUser.setProfilePic(pic);
        savedUser.setUpdatedAt(LocalDateTime.now());
        userRepo.save(savedUser);

        return UserUpdateResponse.builder()
                .id(savedUser.getId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .phoneNumber(savedUser.getPhoneNumber())
                .location(savedUser.getLocation())
                .bio(savedUser.getBio())
                .build();
    }


    // user login
    public UserLoginResponse userLogin(UserLoginRequest request)  {
        User user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User with email " + request.getEmail() + " does not exist"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.error("Invalid password");
            throw new RuntimeException("Invalid password");
        }

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user).getToken();

        return UserLoginResponse.builder()
                .id(user.getId())
                .role(Role.USER)
                .email(user.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // view user profile pic
    public ProfilePicResponse viewProfilePic(UUID id) throws IOException {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User with id " + id + " does not exist"));
        return ProfilePicResponse.builder()
                .id(user.getProfilePic().getId())
                .imageUrl(user.getProfilePic().getImageUrl())
                .imagePublicId(user.getProfilePic().getImagePublicId())
                .build();
    }

    // to view user details
    public UserDetailsResponse viewUserDetails(UUID id , String email){
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User with id " + id + " does not exist"));

        if (!user.getEmail().equals(email)) {
            log.error("Invalid email");
            throw new RuntimeException("Invalid email");
        }

        return UserDetailsResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .location(user.getLocation())
                .bio(user.getBio())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .profilePic(user.getProfilePic() != null ? ProfilePicResponse.builder()
                        .id(user.getProfilePic().getId())
                        .imageUrl(user.getProfilePic().getImageUrl())
                        .imagePublicId(user.getProfilePic().getImagePublicId())
                        .build() : null)
                .build();
    }


    // check if usr details exists
    private boolean userAlreadyExists(String email) {
        log.info("checking if user mail exists: {}", email);
        return userRepo.existsByEmail(email);
    }

    // now for phone number
    private boolean phoneNumberExists(String phoneNumber) {
        log.info("checking if phone number exists: {}", phoneNumber);
        if (isValidGhanaPhoneNumber(phoneNumber)) {
            return true;
        }else {
            log.error("Invalid phone number, Phone number is in correct format");
            return false;
        }
    }


    /** Upload a list of files starting at a given displayOrder offset */
    private ProfilePic uploadProfilePic(MultipartFile profilePic, User user) throws IOException {

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

    public static boolean isValidGhanaPhoneNumber(String phoneNumber) {
        // Matches: 0XXXXXXXXX, +233XXXXXXXXX, 233XXXXXXXXX
        // Networks: 020, 023, 024, 025, 026, 027, 028, 029, 050, 053, 054, 055, 056, 057, 059
        String regex = "^(\\+233|233|0)(2[0-9]|5[0-9])[0-9]{7}$";
        return phoneNumber != null && phoneNumber.matches(regex);
    }

    private boolean phoneNumberAlreadyRegistered(String phoneNumber) {
        log.info("checking if phone number exists: {}", phoneNumber);
        return userRepo.existsByPhoneNumber(phoneNumber);
    }


}
