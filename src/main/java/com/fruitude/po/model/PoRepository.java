package com.fruitude.po.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;


public interface PoRepository  extends JpaRepository<PoVO, Integer>  {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PoVO p where p.poId=:id")
    java.util.Optional<PoVO> lockForReceiving(@org.springframework.data.repository.query.Param("id") Integer id);
	
	boolean existsByPoNo(String poNo);
	
	List<PoVO> getByPoStatus(Byte poStatus);

	// 同時符合採購單狀態與驗收狀態；ReceivingService 用來查申請通過、尚未驗收的採購單
	List<PoVO> findByPoStatusAndInboundStatus(Byte poStatus, Byte inboundStatus);
	
	// 以下三個給 PoNoController 的條件查詢使用
	Optional<PoVO> findByPoNo(String poNo);

	// 查詢條件是 PoVO.vendor（VendorVO 物件）的 vendorId
	List<PoVO> findByVendor_VendorIdOrderByPoIdDesc(Integer vendorId);

	// 查詢條件是 PoVO.poEmployeeId（Employee 物件）的 employeeId
	List<PoVO> findByPoEmployeeId_EmployeeIdOrderByPoIdDesc(Integer employeeId);

	// 取採購單編號開頭相同（同一天）且編號最大的一筆，用來產生下一個流水號
	Optional<PoVO> findTopByPoNoStartingWithOrderByPoNoDesc(String poNoPrefix);
}
