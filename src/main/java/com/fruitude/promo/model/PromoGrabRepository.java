package com.fruitude.promo.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoGrabRepository extends JpaRepository<PromoGrab, Integer> {

	Optional<PromoGrab> findByPromoProjectIdAndMemberId(Integer promoProjectId, Integer memberId);

	// 已經搶到的名額，依序號排列（後台方塊畫面用）
	List<PromoGrab> findByPromoProjectIdAndSlotNoIsNotNullOrderBySlotNo(Integer promoProjectId);

	// 刪活動前先刪參加紀錄（需要在交易中呼叫）
	@Modifying
	@Query("DELETE FROM PromoGrab g WHERE g.promoProjectId = :promoProjectId")
	int deleteByPromoProjectId(@Param("promoProjectId") Integer promoProjectId);
}
