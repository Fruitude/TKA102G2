// https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

package com.fruitude.product.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {
    @Query(value = """
        SELECT i.image_id FROM product_image i
        JOIN product_sku s ON s.sku_id=i.sku_id
        WHERE s.product_id=:productId AND OCTET_LENGTH(i.image_data)>0
        ORDER BY s.sku_id,COALESCE(i.sort_order,0),i.image_id LIMIT 1
        """, nativeQuery = true)
    java.util.Optional<Integer> findFirstImageIdForProduct(
        @org.springframework.data.repository.query.Param("productId") Integer productId);

    @Query(value = "SELECT image_data AS imageData, image_type AS imageType FROM product_image WHERE image_id = :id", nativeQuery = true)
    java.util.Optional<ProductImageSource> findImageSource(@org.springframework.data.repository.query.Param("id") Integer id);
    // Read only the image bytes; loading an entity also fetches its SKU/product relations.
    @Query(value = "SELECT image_data AS imageData FROM product_image WHERE image_id = :id", nativeQuery = true)
    java.util.Optional<ProductThumbnailSource> findThumbnailSource(@org.springframework.data.repository.query.Param("id") Integer id);


    public interface ProductImageSource {

    byte[] getImageData();
    String getImageType();

    }

    public interface ProductThumbnailSource {

    byte[] getImageData();

    }

}
