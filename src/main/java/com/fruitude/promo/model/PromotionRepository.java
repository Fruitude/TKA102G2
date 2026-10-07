package com.fruitude.promo.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Integer> {

	// 後台活動詳細頁用：某個活動底下的活動商品，連同商品名稱、規格名稱與原價
	interface PromotionRow {
		Integer getPromotionId();
		Integer getProductId();
		Integer getSkuId();
		String getProductName();
		String getSkuName();
		Integer getOriginalPrice();
		Integer getPromoPrice();
	}

	// 後台「新增商品」挑選規格用：規格、所屬商品名稱與原價
	interface SkuOption {
		Integer getProductId();
		Integer getSkuId();
		String getProductName();
		String getSkuName();
		Integer getPrice();
	}

	// 某個活動底下的所有活動商品
	List<Promotion> findByPromoProjectIdOrderByPromotionId(Integer promoProjectId);

	@Query(value = """
			SELECT pm.promotion_id AS promotionId, p.product_id AS productId, pm.sku_id AS skuId, p.product_name AS productName,
			       s.sku_name AS skuName, s.price AS originalPrice, pm.promo_price AS promoPrice
			FROM promotion pm
			JOIN product_sku s ON s.sku_id = pm.sku_id
			JOIN product p ON p.product_id = s.product_id
			WHERE pm.promo_project_id = :promoProjectId
			ORDER BY pm.promotion_id
			""", nativeQuery = true)
	List<PromotionRow> findRowsByProject(@Param("promoProjectId") Integer promoProjectId);

	// 可以加入這個活動的規格：原價大於 0、還沒在這個活動裡。
	// 「已經在活動裡」的判斷是 sku_id 與 product_id 都相同；規格名稱、商品名稱相同不代表重複（不同商品可以有同名的規格）。
	// 用 NOT EXISTS 而不是 NOT IN：NOT IN 遇到 NULL 會讓整個清單變空。
	// 可用商品名稱、規格名稱，或規格編號、商品編號（完全相符）搜尋，空字串代表不限
	@Query(value = """
			SELECT p.product_id AS productId, s.sku_id AS skuId, p.product_name AS productName, s.sku_name AS skuName, s.price AS price
			FROM product_sku s JOIN product p ON p.product_id = s.product_id
			WHERE s.price > 0
			  AND NOT EXISTS (SELECT 1 FROM promotion pm JOIN product_sku s2 ON s2.sku_id = pm.sku_id
			                   WHERE pm.promo_project_id = :promoProjectId
			                     AND s2.sku_id = s.sku_id AND s2.product_id = s.product_id)
			  AND (:keyword = '' OR p.product_name LIKE CONCAT('%', :keyword, '%')
			       OR CAST(p.product_id AS CHAR) = :keyword OR CAST(s.sku_id AS CHAR) = :keyword
			       OR s.sku_name LIKE CONCAT('%', :keyword, '%'))
			ORDER BY p.product_id, s.sku_id
			""", nativeQuery = true)
	List<SkuOption> findSkuOptions(@Param("promoProjectId") Integer promoProjectId, @Param("keyword") String keyword);

	// 前台採用的活動價：規格目前有效的最低活動價（活動類型「指定商品」、已啟用、現在在活動期間內，
	// 且活動價必須大於 0、低於規格原價，否則不採用）
	interface ActivePrice {
		Integer getSkuId();
		Integer getPromoPrice();
	}

	@Query(value = """
			SELECT pm.sku_id AS skuId, MIN(pm.promo_price) AS promoPrice
			FROM promotion pm
			JOIN promo_project pj ON pj.promo_project_id = pm.promo_project_id
			JOIN product_sku s ON s.sku_id = pm.sku_id
			WHERE pm.sku_id IN (:skuIds)
			  AND pj.promo_type = 'SKU' AND pj.status = 1
			  AND pj.promo_project_start <= :now AND pj.promo_project_end >= :now
			  AND pm.promo_price > 0 AND pm.promo_price < s.price
			GROUP BY pm.sku_id
			""", nativeQuery = true)
	List<ActivePrice> findActivePrices(@Param("skuIds") java.util.Collection<Integer> skuIds,
			@Param("now") java.time.LocalDateTime now);

	// 這個活動裡是不是已經有這個規格（同一活動同一規格只能一筆）
	Optional<Promotion> findByPromoProjectIdAndSkuId(Integer promoProjectId, Integer skuId);

	// 刪除某個活動底下的所有活動商品（刪活動前要先刪，需要在交易中呼叫）
	long deleteByPromoProjectId(Integer promoProjectId);

	// 某個規格的原價（驗證促銷價必須低於原價用）
	@Query(value = "SELECT price FROM product_sku WHERE sku_id = :skuId", nativeQuery = true)
	Integer findSkuPrice(@Param("skuId") Integer skuId);
}
