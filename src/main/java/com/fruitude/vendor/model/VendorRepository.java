package com.fruitude.vendor.model;

import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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


	// 前台小農資訊頁：勾選顯示且啟用中的供應商，依編號排序
	List<VendorVO> findByShowInFarmersTrueAndIsActiveOrderByVendorId(Byte isActive);

	// 小農圖片的二進位內容不放在 VendorVO（避免每次查供應商都讀出大欄位），另外用 native query 讀寫
	@Query(value = "SELECT farmer_image_data FROM vendor WHERE farmer_image_path = :path AND farmer_image_data IS NOT NULL LIMIT 1", nativeQuery = true)
	byte[] findFarmerImageData(@Param("path") String path);

	@Modifying
	@Query(value = "UPDATE vendor SET farmer_image_data = :data WHERE vendor_id = :vendorId", nativeQuery = true)
	void updateFarmerImageData(@Param("vendorId") Integer vendorId, @Param("data") byte[] data);

	@Query(value = "SELECT farmer_image_path FROM vendor WHERE farmer_image_path IS NOT NULL", nativeQuery = true)
	List<String> findAllFarmerImagePaths();
}
