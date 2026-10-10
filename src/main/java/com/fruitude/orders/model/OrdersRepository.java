package com.fruitude.orders.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;

public interface OrdersRepository extends JpaRepository<Orders, Integer>{

	// 會員自己的訂單，新的在前（購買清單頁用）
	List<Orders> findByMemberIdOrderByOrdersIdDesc(Integer memberId);
	//注意，不用寫 SessionFactory/Session 下 HQL，Spring 已經包裝好，
	//使用 @Query 即可下指令
	@Query("SELECT O AS orders, M AS member FROM Orders O JOIN MemberVO M ON O.memberId = M.memberId order by O.ordersId")
	List<Tuple> findAllWithJoin();

	// 後台訂單管理搜尋與分頁用（沒填搜尋條件就是全部訂單）；Tuple 投影不能自動推出總筆數，所以要自己寫 countQuery。
	// 這裡不寫 order by：排序由呼叫端放在 Pageable 的 Sort 裡（見 OrdersService.search），Spring 會自動接上去。
	// 條件沒填就用「不篩選」的值（0 或空字串）讓那一項條件恆成立，
	@Query(value = "SELECT O AS orders, M AS member FROM Orders O JOIN MemberVO M ON O.memberId = M.memberId "
			+ "WHERE (:ordersId = 0 OR O.ordersId = :ordersId) "
			+ "AND (:memberName = '' OR LOCATE(:memberName, M.memberName) > 0) "
			+ "AND (:receiverName = '' OR LOCATE(:receiverName, O.receiverName) > 0) "
			+ "AND (:phoneNumber = '' OR LOCATE(:phoneNumber, O.phoneNumber) > 0) "
			+ "AND (:statusFilter = 0 OR O.ordersStatus IN :statuses)",
			countQuery = "SELECT COUNT(O) FROM Orders O JOIN MemberVO M ON O.memberId = M.memberId "
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

	// 鎖住這張訂單的資料列（SELECT … FOR UPDATE）直到交易結束。
	// 改訂單狀態並連動庫存時，要先用這個方法讀「改之前的狀態」：同一張訂單同時被兩個人改狀態，後來的會等前一個 commit，
	// 再讀到新的狀態，就不會兩邊都看到「待出貨」而重複扣庫存。必須在交易裡呼叫
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM Orders o WHERE o.ordersId = :ordersId")
	Optional<Orders> findByIdForUpdate(@Param("ordersId") Integer ordersId);

	@Modifying
	@Query("UPDATE Orders o SET o.ordersStatus = :ordersStatus WHERE o.ordersId = :ordersId")
	int updateStatus(@Param("ordersId") Integer ordersId, @Param("ordersStatus") Integer ordersStatus);
}
