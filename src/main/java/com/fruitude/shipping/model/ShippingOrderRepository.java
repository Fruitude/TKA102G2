package com.fruitude.shipping.model;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fruitude.orders.model.Orders;

import jakarta.persistence.LockModeType;

public interface ShippingOrderRepository extends JpaRepository<Orders,Integer>{

	// 指定狀態的訂單，只查 pageable 指定的那一頁；排序也由 pageable 帶入
	Page<Orders> findByOrdersStatus(Integer ordersStatus, Pageable pageable);

	// 出貨前鎖住這張訂單的資料列（FOR UPDATE）並取出，一直到交易結束才放開
	// 鎖住期間別人對同一張訂單出貨、改狀態都要等這個交易結束，所以鎖住之後讀到的狀態才是準的
	// 必須在交易裡呼叫（ShippingService 的 shipOrders）
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Orders o where o.ordersId = :ordersId")
	Optional<Orders> lockOrders(@Param("ordersId") Integer ordersId);

	// 出貨時把訂單狀態由待出貨（0）改成已出貨（1），回傳更新的筆數
	// 只更新 orders_status 一個欄位，不把整筆訂單寫回去，才不會蓋掉訂單其他欄位的修改
	// 條件帶著狀態還是 0：回傳 0 代表訂單不存在或已經不是待出貨，呼叫端要丟例外讓整筆出貨還原
	// 必須在交易裡呼叫（ShippingService 的 shipOrders）
	@Modifying
	@Query("update Orders o set o.ordersStatus = 1 where o.ordersId = :ordersId and o.ordersStatus = 0")
	int updateStatusToShipped(@Param("ordersId") Integer ordersId);

}
