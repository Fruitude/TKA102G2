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

	// 後台訂單管理搜尋與分頁用（沒填搜尋條件就是全部訂單）；Tuple 投影不能自動推出總筆數，所以要自己寫 countQuery。
	// 條件沒填就用「不篩選」的值（0 或空字串）讓那一項條件恆成立，
	@Query(value = "SELECT O AS orders, M AS member FROM Orders O JOIN Member M ON O.memberId = M.memberId "
			+ "WHERE (:ordersId = 0 OR O.ordersId = :ordersId) "
			+ "AND (:memberName = '' OR LOCATE(:memberName, M.memberName) > 0) "
			+ "AND (:receiverName = '' OR LOCATE(:receiverName, O.receiverName) > 0) "
			+ "AND (:phoneNumber = '' OR LOCATE(:phoneNumber, O.phoneNumber) > 0) "
			+ "AND (:statusFilter = 0 OR O.ordersStatus IN :statuses) "
			+ "order by O.ordersDate DESC",
			countQuery = "SELECT COUNT(O) FROM Orders O JOIN Member M ON O.memberId = M.memberId "
			+ "WHERE (:ordersId = 0 OR O.ordersId = :ordersId) "
			+ "AND (:memberName = '' OR LOCATE(:memberName, M.memberName) > 0) "
			+ "AND (:receiverName = '' OR LOCATE(:receiverName, O.receiverName) > 0) "
			+ "AND (:phoneNumber = '' OR LOCATE(:phoneNumber, O.phoneNumber) > 0) "
			+ "AND (:statusFilter = 0 OR O.ordersStatus IN :statuses)")
	Page<Tuple> search(@Param("ordersId") int ordersId,
			@Param("memberName") String memberName,
			@Param("receiverName") String receiverName,
			@Param("phoneNumber") String phoneNumber,
			@Param("statusFilter") int statusFilter,
			@Param("statuses") List<Integer> statuses,
			Pageable pageable);

	@Modifying
	@Query("UPDATE Orders o SET o.ordersStatus = :ordersStatus WHERE o.ordersId = :ordersId")
	int updateStatus(@Param("ordersId") Integer ordersId, @Param("ordersStatus") Integer ordersStatus);
}
