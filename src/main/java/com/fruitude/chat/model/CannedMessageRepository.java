package com.fruitude.chat.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CannedMessageRepository extends JpaRepository<CannedMessage, Integer> {

	// 可以使用的罐頭訊息：所屬類別是顯示狀態（canned_category.status = 1），依顯示順序、編號排序
	// 類別只用在這個條件，所以用 native query 直接 join，不另外建類別的 entity
	@Query(value = "select cm.* from canned_message cm "
			+ "join canned_category cc on cc.canned_category_id = cm.canned_category_id "
			+ "where cc.status = 1 "
			+ "order by cm.sort_order, cm.canned_id", nativeQuery = true)
	List<CannedMessage> findAvailable();

}
