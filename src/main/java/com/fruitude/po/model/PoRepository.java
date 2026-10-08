package com.fruitude.po.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fruitude.employee.model.Employee;


public interface PoRepository  extends JpaRepository<PoVO, Integer>  {
	
	boolean existsByPoNo(String poNo);
	
	List<PoVO> getByPoStatus(Byte poStatus);
	
	List<PoVO> findByPoEmployeeId(Employee employee);

	// 以下四個給 PoNoController 的條件查詢使用
	Optional<PoVO> findByPoNo(String poNo);

	// 查詢條件是 PoVO.vendor（VendorVO 物件）的 vendorId
	List<PoVO> findByVendor_VendorIdOrderByPoIdDesc(Integer vendorId);

	// 查詢條件是 PoVO.poEmployeeId（Employee 物件）的 employeeId
	List<PoVO> findByPoEmployeeId_EmployeeIdOrderByPoIdDesc(Integer employeeId);

	// 供應商名稱模糊查詢（名稱包含關鍵字）
	List<PoVO> findByVendor_VendorNameContainingOrderByPoIdDesc(String vendorName);

	// 取採購單編號開頭相同（同一天）且編號最大的一筆，用來產生下一個流水號
	Optional<PoVO> findTopByPoNoStartingWithOrderByPoNoDesc(String poNoPrefix);
}
