// https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

package com.fruitude.product.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {
    // A content version is looked up only when absent from the version map, not on every image GET.
    // Direct SQL imports must invalidate the application caches or restart the application.
    @Query(value = "SELECT SHA2(image_data,256) FROM product_image WHERE image_id=:id AND OCTET_LENGTH(image_data)>0", nativeQuery = true)
    java.util.Optional<String> findImageFingerprint(@org.springframework.data.repository.query.Param("id") Integer id);

    @Query(value = """
        SELECT (SELECT i.image_id FROM product_sku s JOIN product_image i ON i.sku_id=s.sku_id
          WHERE s.product_id=p.product_id AND OCTET_LENGTH(i.image_data)>0
            AND ((p.status=1 AND s.status IN (1,2,3,4,6)) OR (p.status=2 AND s.status=6))
          ORDER BY s.sku_id,COALESCE(i.sort_order,0),i.image_id LIMIT 1) AS imageId
        FROM product p WHERE p.status IN (1,2)
        ORDER BY p.product_id LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    java.util.List<Integer> findWarmupImageIds(@org.springframework.data.repository.query.Param("offset") int offset,
        @org.springframework.data.repository.query.Param("limit") int limit);
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
