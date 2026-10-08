package com.fruitude.promo.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PromoRepository extends JpaRepository<PromoProject, Integer> {

	// 依開始時間由新到舊
	@Query("FROM PromoProject ORDER BY promoProjectStart DESC")
	List<PromoProject> findAllByStartDateDesc();

	// 這個活動在會員購物金異動紀錄（member_credit_transaction）裡有幾筆。
	// 那張表用外鍵指到 promo_project，有紀錄的活動不能刪，也不該刪（那是購物金的帳務紀錄）
	@Query(value = "SELECT COUNT(*) FROM member_credit_transaction WHERE promo_project_id = :id", nativeQuery = true)
	long countCreditTransactions(@Param("id") Integer id);

	// 刪除這個活動的購物金異動紀錄（刪活動前要先刪，需要在交易中呼叫）
	@Modifying
	@Query(value = "DELETE FROM member_credit_transaction WHERE promo_project_id = :id", nativeQuery = true)
	int deleteCreditTransactions(@Param("id") Integer id);

	// 某類型、已啟用、且目前時間在活動期間內的活動，最低消費門檻低的排前面
	@Query("FROM PromoProject WHERE promoType = :type AND status = 1 "
			+ "AND promoProjectStart <= :now AND promoProjectEnd >= :now ORDER BY minOrderAmount ASC")
	List<PromoProject> findActiveByType(@Param("type") String type, @Param("now") LocalDateTime now);

	// 已啟用、且目前時間在活動期間內的全部活動，快結束的排前面（首頁活動區塊用）
	@Query("FROM PromoProject WHERE status = 1 AND promoProjectStart <= :now AND promoProjectEnd >= :now "
			+ "ORDER BY promoProjectEnd ASC, promoProjectId ASC")
	List<PromoProject> findActive(@Param("now") LocalDateTime now);

	@Query("FROM PromoProject WHERE promoProjectId = :id ORDER BY promoProjectStart DESC")
	List<PromoProject> searchById(@Param("id") Integer id);

	@Query("FROM PromoProject WHERE promoProjectTitle LIKE CONCAT('%', :kw, '%') ORDER BY promoProjectStart DESC")
	List<PromoProject> searchByTitle(@Param("kw") String kw);

	@Query("FROM PromoProject WHERE promoProjectContext LIKE CONCAT('%', :kw, '%') ORDER BY promoProjectStart DESC")
	List<PromoProject> searchByContext(@Param("kw") String kw);

	// 開始時間落在 [from, to) 之間（一整天）
	@Query("FROM PromoProject WHERE promoProjectStart >= :from AND promoProjectStart < :to ORDER BY promoProjectStart DESC")
	List<PromoProject> searchByStartBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// 結束時間落在 [from, to) 之間（一整天）
	@Query("FROM PromoProject WHERE promoProjectEnd >= :from AND promoProjectEnd < :to ORDER BY promoProjectStart DESC")
	List<PromoProject> searchByEndBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}
