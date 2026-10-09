package com.fruitude.promo.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MemberPromoUsageRepository extends JpaRepository<MemberPromoUsage, Integer> {

	// 這個會員這一年有沒有用過這種活動
	boolean existsByMemberIdAndUsageYearAndPromoType(Integer memberId, Integer usageYear, String promoType);

	// 訂單取消或退款：刪除這筆訂單用掉的紀錄，把使用資格還給會員。回傳刪掉幾筆（沒用過壽星月的訂單是 0）
	@Transactional
	@Modifying
	@Query("DELETE FROM MemberPromoUsage u WHERE u.ordersId = :ordersId")
	int deleteByOrdersId(@Param("ordersId") Integer ordersId);
}
