// https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

package com.fruitude.product.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {
    @Query(value = "SELECT image_data AS imageData, image_type AS imageType FROM product_image WHERE image_id = :id", nativeQuery = true)
    java.util.Optional<ProductImageSource> findImageSource(@org.springframework.data.repository.query.Param("id") Integer id);
    // Read only the image bytes; loading an entity also fetches its SKU/product relations.
    @Query(value = "SELECT image_data AS imageData FROM product_image WHERE image_id = :id", nativeQuery = true)
    java.util.Optional<ProductThumbnailSource> findThumbnailSource(@org.springframework.data.repository.query.Param("id") Integer id);

}
