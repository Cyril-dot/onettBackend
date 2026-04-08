package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal;
import com.marketPlace.MarketPlace.Service.SellerProductService;
import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.Enums.ProductStatus;
import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
public class SellerProductController {

    private final SellerProductService sellerProductService;

    // ═══════════════════════════════════════════════════════════
    //  CATEGORY ENDPOINTS
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestPart("request") @Valid CreateCategoryRequest request,
            @RequestPart(value = "iconFile", required = false) MultipartFile iconFile
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        CategoryResponse response =
                sellerProductService.createCategory(request, iconFile, sellerId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", response));
    }

    @PutMapping(value = "/categories/{categoryId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID categoryId,
            @RequestPart("request") @Valid CreateCategoryRequest request,
            @RequestPart(value = "iconFile", required = false) MultipartFile iconFile
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        CategoryResponse response =
                sellerProductService.updateCategory(categoryId, request, iconFile, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<String>> deleteCategory(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID categoryId
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        String message = sellerProductService.deleteCategory(categoryId, sellerId);

        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getSellerCategories(
            @AuthenticationPrincipal AdminPrincipal principal
    ) {
        UUID sellerId = principal.getSellerId();

        List<CategoryResponse> categories =
                sellerProductService.getSellerCategories(sellerId);

        return ResponseEntity.ok(ApiResponse.success("Categories retrieved", categories));
    }

    // ═══════════════════════════════════════════════════════════
    //  PRODUCT ENDPOINTS
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateProductResponse>> addProduct(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestPart("request") @Valid CreateProductRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "videoFile", required = false) MultipartFile videoFile
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        CreateProductResponse response =
                sellerProductService.addProduct(request, images, videoFile, sellerId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product added successfully", response));
    }

    @PutMapping(value = "/products/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateProductResponse>> updateProduct(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId,
            @RequestPart("request") @Valid UpdateProductRequest request,
            @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages,
            @RequestPart(value = "videoFile", required = false) MultipartFile videoFile
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        CreateProductResponse response =
                sellerProductService.updateProduct(productId, request, newImages, videoFile, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @PutMapping(value = "/products/{productId}/images/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateProductResponse>> replaceAllImages(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId,
            @RequestPart("images") List<MultipartFile> images
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        CreateProductResponse response =
                sellerProductService.replaceAllImages(productId, images, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Images replaced successfully", response));
    }

    @PostMapping(value = "/products/{productId}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateProductResponse>> uploadProductVideo(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId,
            @RequestPart("videoFile") MultipartFile videoFile
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        CreateProductResponse response =
                sellerProductService.uploadProductVideo(productId, videoFile, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Video uploaded successfully", response));
    }

    @DeleteMapping("/products/{productId}/video")
    public ResponseEntity<ApiResponse<CreateProductResponse>> deleteProductVideo(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        CreateProductResponse response =
                sellerProductService.deleteProductVideo(productId, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Video deleted successfully", response));
    }

    @PatchMapping("/products/{productId}/stock")
    public ResponseEntity<ApiResponse<CreateProductResponse>> updateStock(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId,
            @RequestParam @Min(0) int stock
    ) {
        UUID sellerId = principal.getSellerId();

        CreateProductResponse response =
                sellerProductService.updateStock(productId, stock, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Stock updated successfully", response));
    }

    @PatchMapping("/products/{productId}/status")
    public ResponseEntity<ApiResponse<CreateProductResponse>> updateProductStatus(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId,
            @RequestParam @NotNull ProductStatus status
    ) {
        UUID sellerId = principal.getSellerId();

        CreateProductResponse response =
                sellerProductService.updateProductStatus(productId, status, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Product status updated", response));
    }

    @PatchMapping("/products/{productId}/stock-status")
    public ResponseEntity<ApiResponse<CreateProductResponse>> updateStockStatus(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId,
            @RequestParam @NotNull StockStatus stockStatus,
            @RequestParam(required = false) Integer availableInDays
    ) {
        UUID sellerId = principal.getSellerId();

        CreateProductResponse response =
                sellerProductService.updateStockStatus(productId, stockStatus, availableInDays, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Stock status updated", response));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<String>> deleteProduct(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId
    ) throws IOException {

        UUID sellerId = principal.getSellerId();

        String message = sellerProductService.deleteProduct(productId, sellerId);

        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC / BROWSING ENDPOINTS
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getMyProducts(
            @AuthenticationPrincipal AdminPrincipal principal
    ) {
        UUID sellerId = principal.getSellerId();

        List<ProductSummaryResponse> products =
                sellerProductService.getMyProducts(sellerId);

        return ResponseEntity.ok(ApiResponse.success("Products retrieved", products));
    }

    @GetMapping("/products/by-status")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getProductsByStatus(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam @NotNull ProductStatus status
    ) {
        UUID sellerId = principal.getSellerId();

        List<ProductSummaryResponse> products =
                sellerProductService.getMyProductsByStatus(sellerId, status);

        return ResponseEntity.ok(ApiResponse.success("Products retrieved", products));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailsResponse>> getProductDetails(
            @PathVariable UUID productId
    ) {
        ProductDetailsResponse response =
                sellerProductService.getProductDetails(productId);

        return ResponseEntity.ok(ApiResponse.success("Product details retrieved", response));
    }

    @GetMapping("/products/category/{slug}")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getProductsByCategory(
            @PathVariable String slug
    ) {
        List<ProductSummaryResponse> products =
                sellerProductService.getProductsByCategory(slug);

        return ResponseEntity.ok(ApiResponse.success("Products retrieved", products));
    }

    @GetMapping("/products/search")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        List<ProductSummaryResponse> products =
                sellerProductService.searchProducts(keyword, brand, minPrice, maxPrice);

        return ResponseEntity.ok(ApiResponse.success("Search results", products));
    }

    @GetMapping("/products/global-search")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> globalSearch(
            @RequestParam String keyword
    ) {
        List<ProductSummaryResponse> products =
                sellerProductService.globalSearch(keyword);

        return ResponseEntity.ok(ApiResponse.success("Search results", products));
    }

    @GetMapping("/products/trending")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getTopTrendingProducts() {
        List<ProductSummaryResponse> products =
                sellerProductService.getTopTrendingProducts();

        return ResponseEntity.ok(ApiResponse.success("Trending products", products));
    }

    @GetMapping("/products/discounted")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getDiscountedProducts() {
        List<ProductSummaryResponse> products =
                sellerProductService.getDiscountedProducts();

        return ResponseEntity.ok(ApiResponse.success("Discounted products", products));
    }

    @GetMapping("/products/low-stock")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getLowStockProducts(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(defaultValue = "5") @Min(0) int threshold
    ) {
        UUID sellerId = principal.getSellerId();

        List<ProductSummaryResponse> products =
                sellerProductService.getLowStockProducts(sellerId, threshold);

        return ResponseEntity.ok(ApiResponse.success("Low stock products", products));
    }
}