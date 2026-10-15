package com.fruitude.po.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface PoRepository  extends JpaRepository<PoVO, Integer>  {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PoVO p where p.poId=:id")
    java.util.Optional<PoVO> lockForReceiving(@org.springframework.data.repository.query.Param("id") Integer id);
	
	boolean existsByPoNo(String poNo);
	
	List<PoVO> getByPoStatus(Byte poStatus);

	// 同時符合採購單狀態與驗收狀態；ReceivingService 用來查申請通過、尚未驗收的採購單
	List<PoVO> findByPoStatusAndInboundStatus(Byte poStatus, Byte inboundStatus);
	
	// 審核用：採購單還是待審核（poStatus = 0）時才改成新的狀態，回傳更新的筆數
	// 回傳 0 代表採購單不存在或已經審核過；條件和更新是同一個 UPDATE，兩個人同時審核時只有一個會成功
	// 必須在交易裡呼叫（PoReviewService 的審核方法）；更新後清掉已經查出來的舊資料，之後再查才會是新的狀態
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update PoVO p set p.poStatus = :newStatus where p.poId = :poId and p.poStatus = 0")
	int updateStatusIfPending(@Param("poId") Integer poId, @Param("newStatus") Byte newStatus);

	// 修改採購單存檔前鎖住這張採購單的資料列（FOR UPDATE），一直到交易結束才放開；只有還是待審核（po_status = 0）的單才查得到
	// 回傳空的代表採購單不存在或已經審核過。鎖住期間審核（updateStatusIfPending）要等修改的交易結束才能改狀態，
	// 審核先改了狀態的話這裡就查不到，所以不會發生「存檔存到一半被審核通過」
	// 必須在交易裡呼叫（PoService 的 updatePoWithDetails）
	@Query(value = "SELECT id FROM purchaseorder WHERE id = :poId AND po_status = 0 FOR UPDATE", nativeQuery = true)
	List<Integer> lockPendingPo(@Param("poId") Integer poId);

	// 驗收存檔前鎖住這張採購單的資料列（FOR UPDATE），一直到交易結束才放開；只有申請通過（po_status = 1）且尚未驗收（inbound_status = 0）的單才查得到
	// 回傳空的代表採購單不存在或已經驗收過。兩個人同時驗收同一張單時，後到的要等前一個存完，這時已經查不到，庫存不會被加兩次
	// 必須在交易裡呼叫（ReceivingService 的 receive）
	@Query(value = "SELECT id FROM purchaseorder WHERE id = :poId AND po_status = 1 AND inbound_status = 0 FOR UPDATE", nativeQuery = true)
	List<Integer> lockReceivablePo(@Param("poId") Integer poId);

	// 修改驗收紀錄存檔前鎖住這張採購單的資料列（FOR UPDATE），一直到交易結束才放開；只有已結案（po_status = 4）的單才查得到
	// 兩個人同時修改同一張單的驗收紀錄時，後到的要等前一個存完，庫存的差額才不會算錯
	// 必須在交易裡呼叫（ReceivingService 的 receive）
	@Query(value = "SELECT id FROM purchaseorder WHERE id = :poId AND po_status = 4 FOR UPDATE", nativeQuery = true)
	List<Integer> lockClosedPo(@Param("poId") Integer poId);

	// 採購單首頁的複合條件查詢（PoNoController 的 searchPos）：採購單編號、採購單狀態、供應商編號、採購員工編號可以同時使用，條件之間是「而且」
	// 條件沒填就用「不篩選」的值讓那一項條件恆成立，寫法和 OrdersRepository 的 search 相同：
	// poNo 空字串、statusFilter 0（有選狀態時是 1，poStatus 才有作用）、vendorId 0、poEmployeeId 0
	// 採購單編號完全相同才算符合；新的採購單排在前面
	@Query("select p from PoVO p where (:poNo = '' or p.poNo = :poNo) "
			+ "and (:statusFilter = 0 or p.poStatus = :poStatus) "
			+ "and (:vendorId = 0 or p.vendor.vendorId = :vendorId) "
			+ "and (:poEmployeeId = 0 or p.poEmployeeId.employeeId = :poEmployeeId) "
			+ "order by p.poId desc")
	List<PoVO> searchPos(@Param("poNo") String poNo,
			@Param("statusFilter") int statusFilter,
			@Param("poStatus") Byte poStatus,
			@Param("vendorId") int vendorId,
			@Param("poEmployeeId") int poEmployeeId);

	// 歷史驗收紀錄的複合條件查詢（ReceivingService 的 searchClosedPos）：只查已結案（po_status = 4，驗收過）的採購單
	// 採購單編號、供應商編號、驗收狀態、驗收日期區間可以同時使用，條件之間是「而且」；只查 pageable 指定的那一頁，排序也由 pageable 帶入
	// 條件沒填就用「不篩選」的值讓那一項條件恆成立：poNo 空字串、vendorId 0、inboundStatusFilter 0、dateFilter 0
	// 日期區間由 ReceivingService 換算好：startDateTime 含當下，endDateTime 不含（迄日隔天 0 點）；沒查日期時驗收日期是空的單也會列出
	@Query("select p from PoVO p where p.poStatus = 4 "
			+ "and (:poNo = '' or p.poNo = :poNo) "
			+ "and (:vendorId = 0 or p.vendor.vendorId = :vendorId) "
			+ "and (:inboundStatusFilter = 0 or p.inboundStatus = :inboundStatus) "
			+ "and (:dateFilter = 0 or (p.inboundDate >= :startDateTime and p.inboundDate < :endDateTime))")
	Page<PoVO> searchClosedPos(@Param("poNo") String poNo,
			@Param("vendorId") int vendorId,
			@Param("inboundStatusFilter") int inboundStatusFilter,
			@Param("inboundStatus") Byte inboundStatus,
			@Param("dateFilter") int dateFilter,
			@Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTime") LocalDateTime endDateTime,
			Pageable pageable);

	// 取採購單編號開頭相同（同一天）且編號最大的一筆，用來產生下一個流水號
	Optional<PoVO> findTopByPoNoStartingWithOrderByPoNoDesc(String poNoPrefix);
}
