package com.fruitude.logistics.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fruitude.orders.model.Orders;

public interface LogisticsOrderRepository extends JpaRepository<Orders,Integer>{

	// 指定狀態的訂單，只查 pageable 指定的那一頁；排序也由 pageable 帶入
	Page<Orders> findByOrdersStatus(Integer ordersStatus, Pageable pageable);

	// 配送時把訂單狀態由配送中（1）改成已配送（7），回傳更新的筆數
	// 只更新 orders_status 一個欄位，不把整筆訂單寫回去，才不會蓋掉訂單其他欄位的修改
	// 條件帶著狀態還是 1：回傳 0 代表訂單不存在或已經不是配送中，呼叫端要丟例外讓整批配送還原
	// 必須在交易裡呼叫（LogisticsService 的 deliverOrders）
	@Modifying
	@Query("update Orders o set o.ordersStatus = 7 where o.ordersId = :ordersId and o.ordersStatus = 1")
	int updateStatusToDelivered(@Param("ordersId") Integer ordersId);

}
