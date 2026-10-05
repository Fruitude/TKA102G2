package com.fruitude.orders.model;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.Tuple;

public interface OrdersRepository extends JpaRepository<Orders, Integer>{
	//注意，不用寫 SessionFactory/Session 下 HQL，Spring 已經包裝好，
	//使用 @Query 即可下指令
	@Query("SELECT O AS orders, M AS member FROM Orders O JOIN Member M ON O.memberId = M.memberId order by O.ordersId")
	List<Tuple> findAllWithJoin();

	// 後台訂單管理分頁用；Tuple 投影不能自動推出總筆數，所以要自己寫 countQuery
	@Query(value = "SELECT O AS orders, M AS member FROM Orders O JOIN Member M ON O.memberId = M.memberId order by O.ordersId",
			countQuery = "SELECT COUNT(O) FROM Orders O JOIN Member M ON O.memberId = M.memberId")
	Page<Tuple> findPageWithJoin(Pageable pageable);

	@Modifying
	@Query("UPDATE Orders o SET o.ordersStatus = :ordersStatus WHERE o.ordersId = :ordersId")
	int updateStatus(@Param("ordersId") Integer ordersId, @Param("ordersStatus") Integer ordersStatus);
}
