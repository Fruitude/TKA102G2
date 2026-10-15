package com.fruitude.shipping.model;

import java.time.LocalDateTime;
import java.util.Collection;
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

	// 歷史出貨紀錄的查詢：狀態不在 excludedStatuses 裡（排除待出貨與取消），再依查詢條件篩選，只查 pageable 指定的那一頁；排序也由 pageable 帶入
	// 條件沒填就用「不篩選」的值（ordersId 0、ordersStatus -1、receiverName 空字串、dateFilter 0）讓那一項條件恆成立，寫法和 OrdersRepository 的 search 相同
	// 日期區間由 ShippingService 換算好：startDateTime 含當下，endDateTime 不含（迄日隔天 0 點）；沒查日期時訂單日期是空的訂單也會列出
	// 收件人姓名用 locate 找部分文字
	@Query("select o from Orders o where o.ordersStatus not in :excludedStatuses "
			+ "and (:ordersId = 0 or o.ordersId = :ordersId) "
			+ "and (:ordersStatus = -1 or o.ordersStatus = :ordersStatus) "
			+ "and (:receiverName = '' or locate(:receiverName, o.receiverName) > 0) "
			+ "and (:dateFilter = 0 or (o.ordersDate >= :startDateTime and o.ordersDate < :endDateTime))")
	Page<Orders> searchHistoryOrders(@Param("excludedStatuses") Collection<Integer> excludedStatuses,
			@Param("ordersId") int ordersId,
			@Param("ordersStatus") int ordersStatus,
			@Param("receiverName") String receiverName,
			@Param("dateFilter") int dateFilter,
			@Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTime") LocalDateTime endDateTime,
			Pageable pageable);

	// 出貨前鎖住這張訂單的資料列（FOR UPDATE）並取出，一直到交易結束才放開
	// 鎖住期間別人對同一張訂單出貨、改狀態都要等這個交易結束，所以鎖住之後讀到的狀態才是準的
	// 必須在交易裡呼叫（ShippingService 的 shipOrders）
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Orders o where o.ordersId = :ordersId")
	Optional<Orders> lockOrders(@Param("ordersId") Integer ordersId);

	// 出貨時把訂單狀態由待出貨（0）改成已出貨（1），並記下出貨員工編號，回傳更新的筆數
	// 只更新 orders_status、employee_id 兩個欄位，不把整筆訂單寫回去，才不會蓋掉訂單其他欄位的修改
	// 條件帶著狀態還是 0：回傳 0 代表訂單不存在或已經不是待出貨，呼叫端要丟例外讓整筆出貨還原
	// 必須在交易裡呼叫（ShippingService 的 shipOrders）
	@Modifying
	@Query("update Orders o set o.ordersStatus = 1, o.employeeId = :employeeId where o.ordersId = :ordersId and o.ordersStatus = 0")
	int updateStatusToShipped(@Param("ordersId") Integer ordersId, @Param("employeeId") Integer employeeId);

	// 取消出貨時把訂單狀態由配送中（1）改回待出貨（0），並清空出貨員工編號，回傳更新的筆數
	// 和 updateStatusToShipped 一樣只更新 orders_status、employee_id 兩個欄位
	// 條件帶著狀態還是 1：回傳 0 代表訂單不存在或已經不是配送中，呼叫端要丟例外讓整筆取消出貨還原
	// 必須在交易裡呼叫（ShippingService 的 cancelShipOrders）
	@Modifying
	@Query("update Orders o set o.ordersStatus = 0, o.employeeId = null where o.ordersId = :ordersId and o.ordersStatus = 1")
	int updateStatusToPending(@Param("ordersId") Integer ordersId);

}
