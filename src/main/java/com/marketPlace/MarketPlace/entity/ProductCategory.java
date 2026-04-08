package com.marketPlace.MarketPlace.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "product_category")
@Builder
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String name;

    private String slug; // user friendly name for users to use to filter out products

    @OneToOne(cascade = CascadeType.ALL) // Cascades operations (save, delete, etc.)
    @JoinColumn(name = "category_icon_id", referencedColumnName = "id") // Defines the foreign key column name
    private CategoryIcon icon;

    @ManyToOne
    @JoinColumn(name = "seller_id") // Specifies the foreign key column name
    private Seller seller;

    @OneToOne
    @JoinColumn(name = "product_id") // here every product is linked to one category
    private Product product;
}
