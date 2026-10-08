package com.fruitude.vendor.model;

import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorRepository extends JpaRepository<VendorVO, Integer> {

	boolean existsByTaxId(String taxId);

	boolean existsByTaxIdAndVendorIdNot(String taxId, Integer vendorId);
	
	List<VendorVO> findByIsActive(Byte isActive);

	// 產地模糊查詢（origin LIKE %關鍵字%），並限定啟用狀態
	List<VendorVO> findByOriginContainingAndIsActive(String origin, Byte isActive);

	// 聯絡人姓名模糊查詢（contact_person LIKE %關鍵字%），並限定啟用狀態
	List<VendorVO> findByContactPersonContainingAndIsActive(String contactPerson, Byte isActive);
	
	List<VendorVO> findByTaxIdContainingAndIsActive(String taxId, Byte isActive);
	
	@Query("select new map(v.vendorId as vendorId, v.vendorName as vendorName) "
		     + "from VendorVO v where v.isActive = :isActive order by v.vendorId")
		List<Map<String, Object>> findOptionsByIsActive(@Param("isActive") Byte isActive);

}