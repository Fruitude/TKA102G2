package com.fruitude.member.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 購物車資料存取層，只提供會員編號範圍內的查詢與異動。 */
@Repository
public interface ShoppingCartRepository extends JpaRepository<ShoppingCart, Integer> {

	List<ShoppingCart> findByMemberIdOrderByCartIdAsc(Integer memberId);

	// 用 JPQL 直接刪除；衍生的 deleteBy 會等到 flush 才刪，與後面的新增順序相反會撞到唯一鍵。
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ShoppingCart c where c.memberId = :memberId")
	void deleteAllByMember(@Param("memberId") Integer memberId);

	// 過濾掉已不存在的規格，避免外鍵錯誤。
	@Query(value = "SELECT sku_id FROM product_sku WHERE sku_id IN (:skuIds)", nativeQuery = true)
	List<Integer> findExistingSkuIds(@Param("skuIds") List<Integer> skuIds);

	// 每個規格取第一張有圖片資料的圖片編號，供還原購物車時顯示縮圖。
	@Query(value = """
			SELECT i.sku_id AS skuId, MIN(i.image_id) AS imageId
			FROM product_image i
			WHERE i.sku_id IN (:skuIds) AND OCTET_LENGTH(i.image_data) > 0
			GROUP BY i.sku_id
			""", nativeQuery = true)
	List<SkuImageRow> findFirstImageIds(@Param("skuIds") List<Integer> skuIds);

	interface SkuImageRow {
		Integer getSkuId();

		Integer getImageId();
	}
}
