package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import com.marketPlace.MarketPlace.entity.Product;
import com.marketPlace.MarketPlace.entity.Enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepo extends JpaRepository<Product, UUID> {

    // --- Seller queries ---
    List<Product> findBySellerId(UUID sellerId);
    List<Product> findBySellerIdAndProductStatus(UUID sellerId, ProductStatus status);
    long countBySellerId(UUID sellerId);

    List<Product> findTop10ByProductStatusOrderByCreatedAtDesc(ProductStatus status);

    // --- Search & discovery ---
    List<Product> findByNameContainingIgnoreCase(String keyword);
    List<Product> findByBrandIgnoreCase(String brand);
    List<Product> findByBrandIgnoreCaseAndProductStatus(String brand, ProductStatus status);

    // --- Status filtering ---
    List<Product> findByProductStatus(ProductStatus status);
    Optional<Product> findByIdAndProductStatus(UUID id, ProductStatus status);

    // --- Price range filtering ---
    List<Product> findByPriceBetweenAndProductStatus(BigDecimal min, BigDecimal max, ProductStatus status);

    // --- Discount filtering ---
    List<Product> findByDiscountedTrueAndProductStatus(ProductStatus status);

    // --- Stock management ---
    List<Product> findByStockLessThanEqual(Integer threshold);
    List<Product> findByStockEquals(Integer stock);

    // --- Trending / popularity ---
    List<Product> findTop10ByProductStatusOrderByViewsCountDesc(ProductStatus status);

    // --- Increment view count ---
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.viewsCount = p.viewsCount + 1 WHERE p.id = :id")
    void incrementViewCount(UUID id);

    // --- Filtered search with null-safe keyword/brand/status checks ---
    @Query(value = """
            SELECT * FROM public.product p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name ILIKE CONCAT('%', :keyword, '%'))
            AND (:brand IS NULL OR :brand = '' OR p.brand ILIKE :brand)
            AND (:minPrice IS NULL OR p.price >= CAST(:minPrice AS numeric))
            AND (:maxPrice IS NULL OR p.price <= CAST(:maxPrice AS numeric))
            AND (:status IS NULL OR :status = '' OR p.product_status = :status)
            ORDER BY p.created_at DESC
            """, nativeQuery = true)
    List<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("brand") String brand,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("status") String status
    );

    // --- Global full-text search (removed CAST on brand, now plain ILIKE) ---
    @Query(value = """
            SELECT DISTINCT p.* FROM public.product p
            LEFT JOIN public.seller s ON s.id = p.seller_id
            LEFT JOIN public.product_category pc ON pc.product_id = p.id
            WHERE p.product_status = 'ACTIVE'
            AND (
                p.name                   ILIKE CONCAT('%', :keyword, '%')
                OR p.brand               ILIKE CONCAT('%', :keyword, '%')
                OR p.product_description ILIKE CONCAT('%', :keyword, '%')
                OR s.store_name          ILIKE CONCAT('%', :keyword, '%')
                OR s.location            ILIKE CONCAT('%', :keyword, '%')
                OR pc.name               ILIKE CONCAT('%', :keyword, '%')
                OR pc.slug               ILIKE CONCAT('%', :keyword, '%')
            )
            ORDER BY p.views_count DESC
            """, nativeQuery = true)
    List<Product> globalSearch(@Param("keyword") String keyword);

    // --- Seller revenue helper ---
    @Query("SELECT SUM(p.price * p.stock) FROM Product p WHERE p.seller.id = :sellerId")
    BigDecimal calculateInventoryValueBySeller(UUID sellerId);

    // --- Upcoming products ---
    List<Product> findByProductStatusAndStockStatus(ProductStatus productStatus, StockStatus stockStatus);
}