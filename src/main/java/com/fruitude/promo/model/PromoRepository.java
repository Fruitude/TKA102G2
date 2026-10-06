package com.fruitude.promo.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PromoRepository extends JpaRepository<PromoProject, Integer> {

	// 依開始時間由新到舊
	@Query("FROM PromoProject ORDER BY promoProjectStart DESC")
	List<PromoProject> findAllByStartDateDesc();

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
