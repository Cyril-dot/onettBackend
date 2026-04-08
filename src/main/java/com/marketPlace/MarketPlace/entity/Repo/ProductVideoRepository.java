package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.ProductVideo;
import com.marketPlace.MarketPlace.entity.UserProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVideoRepository extends JpaRepository<ProductVideo, UUID> {

}