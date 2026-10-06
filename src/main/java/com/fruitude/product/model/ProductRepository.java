// https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

package com.fruitude.product.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    @Query(value = """
        SELECT p.product_id AS productId, p.product_name AS name, p.product_desc AS description,
               p.product_category_id AS categoryId, s.sku_id AS skuId, s.sku_name AS skuName,
               s.status AS skuStatus, s.price AS price, CASE WHEN s.status = 3 THEN s.stock ELSE NULL END AS stock,
               CASE WHEN s.status = 3 THEN s.inbound_qty ELSE NULL END AS inboundQty,
               CASE WHEN s.status = 3 THEN s.outbound_qty ELSE NULL END AS outboundQty
        FROM product p JOIN product_sku s ON s.product_id = p.product_id
        WHERE p.status = 1 AND s.status IN (1,2,3) AND s.price > 0
          AND (:productId IS NULL OR p.product_id = :productId)
        ORDER BY p.product_id, s.sku_id
        """, nativeQuery = true)
    java.util.List<FrontCatalogRow> findFrontRows(@org.springframework.data.repository.query.Param("productId") Integer productId);

    @Query(value = """
        SELECT i.sku_id AS skuId, i.image_id AS imageId
        FROM product_image i JOIN product_sku s ON s.sku_id = i.sku_id
        WHERE s.product_id IN (:productIds) AND s.status IN (1,2,3) AND s.price > 0
          AND OCTET_LENGTH(i.image_data) > 0
        ORDER BY s.sku_id, COALESCE(i.sort_order,0), i.image_id
        """, nativeQuery = true)
    java.util.List<FrontImageRow> findFrontImages(@org.springframework.data.repository.query.Param("productIds") java.util.List<Integer> productIds);

    @Query(value = "SELECT product_category_id AS categoryId, parent_category_id AS parentId, category_name AS name FROM product_category", nativeQuery = true)
    java.util.List<FrontCategoryRow> findFrontCategories();

    @Query(value = """
        SELECT s.sku_id AS skuId, p.product_name AS name, s.sku_name AS skuName,
               s.price AS price, CASE WHEN s.status = 3 THEN s.stock ELSE NULL END AS stock,
               CASE WHEN s.status = 3 THEN s.inbound_qty ELSE NULL END AS inboundQty,
               CASE WHEN s.status = 3 THEN s.outbound_qty ELSE NULL END AS outboundQty, p.status AS productStatus, s.status AS skuStatus
        FROM product_sku s JOIN product p ON p.product_id = s.product_id
        WHERE s.sku_id IN (:skuIds)
        """, nativeQuery = true)
    java.util.List<LiveSkuRow> findLiveSkus(@org.springframework.data.repository.query.Param("skuIds") java.util.List<Integer> skuIds);

    String STOCK_ABNORMAL = """
        ((COALESCE(s.safety_stock,0) = 0 AND COALESCE(s.stock,0) < 10) 
         OR COALESCE(s.stock,0) > 100
         OR COALESCE(s.stock,0) + COALESCE(s.inbound_qty,0) - COALESCE(s.outbound_qty,0) < COALESCE(s.safety_stock,0)
         OR COALESCE(s.inbound_qty,0) > 50 
         OR COALESCE(s.outbound_qty,0) > 50 
         OR COALESCE(s.safety_stock,0) = 0)
        """;

    String OVERVIEW_FILTER = """
        WHERE (:productStatus IS NULL OR p.status = :productStatus)
          AND (:parentCategoryId IS NULL OR c.product_category_id = :parentCategoryId OR c.parent_category_id = :parentCategoryId)
          AND (:categoryId IS NULL OR p.product_category_id = :categoryId)
          AND (:vendorId IS NULL OR p.vendor_id = :vendorId)
          AND (:minComments IS NULL OR COALESCE(p.all_comment_amount,0) >= :minComments)
          AND (:maxComments IS NULL OR COALESCE(p.all_comment_amount,0) <= :maxComments)
          AND (:ratingBucket IS NULL
            OR (:ratingBucket = -1 AND COALESCE(p.all_comment_amount,0) = 0)
            OR (:ratingBucket >= 0 AND COALESCE(p.all_comment_amount,0) > 0
              AND COALESCE(p.all_comment_star,0) >= p.all_comment_amount * :ratingBucket
              AND (COALESCE(p.all_comment_star,0) < p.all_comment_amount * (:ratingBucket + 1)
                OR (:ratingBucket = 4 AND p.all_comment_star = p.all_comment_amount * 5))))
          AND (:stockFilter = 'all'
            OR (:stockFilter = 'normal' AND NOT EXISTS
                (SELECT 1 FROM product_sku s WHERE s.product_id = p.product_id AND
        """ + STOCK_ABNORMAL + """
                ))
            OR EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.product_id AND (
                (:stockFilter = 'abnormal' AND
        """ + STOCK_ABNORMAL + """
                )
                OR (:stockFilter = 'below-safety' AND (
                    COALESCE(s.stock,0) + COALESCE(s.inbound_qty,0) - COALESCE(s.outbound_qty,0) < COALESCE(s.safety_stock,0)
                    OR (COALESCE(s.safety_stock,0) = 0 AND COALESCE(s.stock,0) < 10)))
                OR (:stockFilter = 'high-stock' AND COALESCE(s.stock,0) > 100)
                OR (:stockFilter = 'inbound' AND COALESCE(s.inbound_qty,0) > 50)
                OR (:stockFilter = 'outbound' AND COALESCE(s.outbound_qty,0) > 50)
                OR (:stockFilter = 'unset-safety' AND COALESCE(s.safety_stock,0) = 0)
            )))
        """;

    @Query(value = """
        SELECT p.product_id AS productId, p.product_name AS productName,
               v.vendor_name AS vendorName, p.status AS status,
               p.all_comment_amount AS allCommentAmount, p.all_comment_star AS allCommentStar,
               (SELECT i.image_id FROM product_sku s JOIN product_image i ON i.sku_id = s.sku_id
                WHERE s.product_id = p.product_id AND i.image_data IS NOT NULL
                ORDER BY s.sku_id, i.sort_order, i.image_id LIMIT 1) AS imageId
        FROM product p LEFT JOIN vendor v ON v.vendor_id = p.vendor_id
        LEFT JOIN product_category c ON c.product_category_id = p.product_category_id
        """ + OVERVIEW_FILTER + """
        ORDER BY
          CASE WHEN :sortBy = 'modified' AND :sortDirection = 'asc' THEN COALESCE(p.updated_at,p.created_at) END ASC,
          CASE WHEN :sortBy = 'modified' AND :sortDirection = 'desc' THEN COALESCE(p.updated_at,p.created_at) END DESC,
          CASE WHEN :sortBy = 'comments' AND :sortDirection = 'asc' THEN COALESCE(p.all_comment_amount,0) END ASC,
          CASE WHEN :sortBy = 'comments' AND :sortDirection = 'desc' THEN COALESCE(p.all_comment_amount,0) END DESC,
          CASE WHEN :sortBy = 'rating' AND :sortDirection = 'asc'
            THEN COALESCE(p.all_comment_star / NULLIF(p.all_comment_amount,0),0) END ASC,
          CASE WHEN :sortBy = 'rating' AND :sortDirection = 'desc'
            THEN COALESCE(p.all_comment_star / NULLIF(p.all_comment_amount,0),0) END DESC,
          p.product_id ASC
        """,
        countQuery = "SELECT COUNT(*) FROM product p LEFT JOIN product_category c ON c.product_category_id = p.product_category_id " + OVERVIEW_FILTER, nativeQuery = true)
    org.springframework.data.domain.Page<ProductOverview> findOverviewPage(
        @org.springframework.data.repository.query.Param("productStatus") Integer productStatus,
        @org.springframework.data.repository.query.Param("parentCategoryId") Integer parentCategoryId,
        @org.springframework.data.repository.query.Param("categoryId") Integer categoryId,
        @org.springframework.data.repository.query.Param("vendorId") Integer vendorId,
        @org.springframework.data.repository.query.Param("stockFilter") String stockFilter,
        @org.springframework.data.repository.query.Param("minComments") Integer minComments,
        @org.springframework.data.repository.query.Param("maxComments") Integer maxComments,
        @org.springframework.data.repository.query.Param("ratingBucket") Integer ratingBucket,
        @org.springframework.data.repository.query.Param("sortBy") String sortBy,
        @org.springframework.data.repository.query.Param("sortDirection") String sortDirection,
        org.springframework.data.domain.Pageable pageable);

    @Query(value = "SELECT product_id AS productId, sku_name AS skuName FROM product_sku WHERE product_id IN (:productIds) ORDER BY product_id, sku_id", nativeQuery = true)
    java.util.List<ProductOverviewSku> findOverviewSkuNames(
        @org.springframework.data.repository.query.Param("productIds") java.util.List<Integer> productIds);

    java.util.List<Product> findByStatusOrderByProductIdAsc(Byte status);

    @Query(value = """
        SELECT s.product_id AS productId, i.image_id AS imageId
        FROM product_sku s JOIN product_image i ON i.sku_id = s.sku_id
        WHERE s.product_id IN (:productIds) AND i.image_data IS NOT NULL
        ORDER BY s.product_id, s.sku_id, i.sort_order, i.image_id
        """, nativeQuery = true)
    java.util.List<ProductOverviewImage> findOverviewImages(
        @org.springframework.data.repository.query.Param("productIds") java.util.List<Integer> productIds);

    @Query(value = """
        SELECT s.product_id AS productId,
          MAX(CASE WHEN COALESCE(s.stock,0) + COALESCE(s.inbound_qty,0) - COALESCE(s.outbound_qty,0)
                        < COALESCE(s.safety_stock,0) THEN 1 ELSE 0 END) AS belowSafety,
          MAX(CASE WHEN COALESCE(s.safety_stock,0) = 0 AND COALESCE(s.stock,0) < 10 THEN 1 ELSE 0 END) AS lowStock,
          MAX(CASE WHEN COALESCE(s.stock,0) > 100 THEN 1 ELSE 0 END) AS highStock,
          MAX(CASE WHEN COALESCE(s.safety_stock,0) = 0 THEN 1 ELSE 0 END) AS unsetSafety,
          MAX(COALESCE(s.inbound_qty,0)) AS maxInbound,
          MAX(COALESCE(s.outbound_qty,0)) AS maxOutbound
        FROM product_sku s WHERE s.product_id IN (:productIds)
        GROUP BY s.product_id ORDER BY s.product_id
        """, nativeQuery = true)
    java.util.List<ProductOverviewStock> findOverviewStock(
        @org.springframework.data.repository.query.Param("productIds") java.util.List<Integer> productIds);

    long countByProductIdIn(java.util.Collection<Integer> productIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.status = :status, p.updatedAt = :updatedAt WHERE p.productId IN :productIds")
    int updatePageStatus(@org.springframework.data.repository.query.Param("productIds") java.util.Collection<Integer> productIds,
        @org.springframework.data.repository.query.Param("status") Byte status,
        @org.springframework.data.repository.query.Param("updatedAt") java.time.LocalDateTime updatedAt);

    boolean existsByVendor_VendorIdAndProductNameIgnoreCase(Integer vendorId, String productName);
    boolean existsByVendor_VendorIdAndProductNameIgnoreCaseAndProductIdNot(Integer vendorId, String productName, Integer productId);

}
