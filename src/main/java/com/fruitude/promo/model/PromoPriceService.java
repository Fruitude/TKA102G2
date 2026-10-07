package com.fruitude.promo.model;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 指定商品促銷的活動價查詢，給前台商品價格（FrontCatalogService）使用。
 * 只負責「查出哪些規格目前有活動價」，不判斷任何其他折扣。
 */
@Service
public class PromoPriceService {

	@Autowired
	private PromotionRepository promotionRepository;

	// 規格編號 → 目前有效的最低活動價；沒有活動價的規格不會出現在結果裡
	public Map<Integer, Integer> findActivePrices(Collection<Integer> skuIds) {
		Map<Integer, Integer> result = new HashMap<>();
		if (skuIds == null || skuIds.isEmpty()) {
			return result;
		}
		for (PromotionRepository.ActivePrice row : promotionRepository.findActivePrices(skuIds, LocalDateTime.now())) {
			result.put(row.getSkuId(), row.getPromoPrice());
		}
		return result;
	}
}
