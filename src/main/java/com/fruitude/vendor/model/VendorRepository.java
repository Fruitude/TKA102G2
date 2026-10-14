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

	// 產地模糊查詢（origin LIKE %關鍵字%），不限啟用狀態
	List<VendorVO> findByOriginContaining(String origin);

	// 聯絡人姓名模糊查詢（contact_person LIKE %關鍵字%），不限啟用狀態
	List<VendorVO> findByContactPersonContaining(String contactPerson);

	// 統一編號模糊查詢（tax_id LIKE %關鍵字%），不限啟用狀態
	List<VendorVO> findByTaxIdContaining(String taxId);
	
	@Query("select new map(v.vendorId as vendorId, v.vendorName as vendorName) "
		     + "from VendorVO v where v.isActive = :isActive order by v.vendorId")
		List<Map<String, Object>> findOptionsByIsActive(@Param("isActive") Byte isActive);

}
