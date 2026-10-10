package com.fruitude.po.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface PoRepository  extends JpaRepository<PoVO, Integer>  {
	
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

	// 以下三個給 PoNoController 的條件查詢使用
	Optional<PoVO> findByPoNo(String poNo);

	// 查詢條件是 PoVO.vendor（VendorVO 物件）的 vendorId
	List<PoVO> findByVendor_VendorIdOrderByPoIdDesc(Integer vendorId);

	// 查詢條件是 PoVO.poEmployeeId（Employee 物件）的 employeeId
	List<PoVO> findByPoEmployeeId_EmployeeIdOrderByPoIdDesc(Integer employeeId);

	// 取採購單編號開頭相同（同一天）且編號最大的一筆，用來產生下一個流水號
	Optional<PoVO> findTopByPoNoStartingWithOrderByPoNoDesc(String poNoPrefix);
}
