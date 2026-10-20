package com.fruitude.chat.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqCategoryRepository extends JpaRepository<FaqCategory, Integer> {

	// 管理頁列表：依編號排序
	List<FaqCategory> findAllByOrderByFaqCategoryIdAsc();

	// 唯一鍵 category_name：新增時用第一個，修改時排除自己用第二個
	boolean existsByCategoryName(String categoryName);

	boolean existsByCategoryNameAndFaqCategoryIdNot(String categoryName, Integer faqCategoryId);

	// 刪除類別前檢查有沒有子類別
	boolean existsByParentCategoryId(Integer parentCategoryId);

}
