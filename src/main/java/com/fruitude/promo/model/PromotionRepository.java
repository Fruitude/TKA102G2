package com.fruitude.promo.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Integer> {

	// 某個活動底下的所有活動商品
	List<Promotion> findByPromoProjectIdOrderByPromotionId(Integer promoProjectId);

	// 刪除某個活動底下的所有活動商品（刪活動前要先刪，需要在交易中呼叫）
	long deleteByPromoProjectId(Integer promoProjectId);
}
