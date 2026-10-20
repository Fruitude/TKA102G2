package com.fruitude.chat.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqRepository extends JpaRepository<Faq, Integer> {

	// 管理頁列表：依類別、顯示順序、編號排序
	List<Faq> findAllByOrderByFaqCategoryIdAscSortOrderAscFaqIdAsc();

	// 拖拉排序時取出同一類別的全部 FAQ
	List<Faq> findByFaqCategoryIdOrderBySortOrderAscFaqIdAsc(Integer faqCategoryId);

	// 唯一鍵 faq_category_id + question：新增時用第一個，修改時排除自己用第二個
	boolean existsByFaqCategoryIdAndQuestion(Integer faqCategoryId, String question);

	boolean existsByFaqCategoryIdAndQuestionAndFaqIdNot(Integer faqCategoryId, String question, Integer faqId);

	// 刪除類別前檢查這個類別底下有沒有 FAQ
	boolean existsByFaqCategoryId(Integer faqCategoryId);

	// 類別內目前最大的顯示順序，沒有 FAQ 時回 0；新增或換類別時排到最後面
	@Query("select coalesce(max(f.sortOrder), 0) from Faq f where f.faqCategoryId = :faqCategoryId")
	int findMaxSortOrder(@Param("faqCategoryId") Integer faqCategoryId);

}
