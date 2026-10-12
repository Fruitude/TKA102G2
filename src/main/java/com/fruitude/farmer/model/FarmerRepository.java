package com.fruitude.farmer.model;

import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmerRepository extends JpaRepository<FarmerVO, Integer> {

	// 前台小農資訊頁：勾選顯示且供應商啟用中的小農，依供應商編號排序；供應商一併取出避免逐筆查詢
	@Query("select f from FarmerVO f join fetch f.vendor v "
			+ "where f.showInFarmers = true and v.isActive = :isActive order by f.vendorId")
	List<FarmerVO> findForFront(@Param("isActive") Byte isActive);

	// 後台列表：全部小農連同供應商
	@Query("select f from FarmerVO f join fetch f.vendor order by f.vendorId")
	List<FarmerVO> findAllWithVendor();

	// 新增小農時的供應商下拉選單：已啟用的供應商；不會讀出 logo
	@Query("select new map(v.vendorId as vendorId, v.vendorName as vendorName, "
			+ "v.contactPerson as contactPerson, v.origin as origin) "
			+ "from VendorVO v where v.isActive = :isActive order by v.vendorId")
	List<Map<String, Object>> findVendorOptions(@Param("isActive") Byte isActive);

	// 已經有小農資料的供應商編號
	@Query("select f.vendorId from FarmerVO f")
	List<Integer> findAllVendorIds();

	// 小農圖片的二進位內容不放在 FarmerVO（避免每次查詢都讀出大欄位），另外用 native query 讀寫
	@Query(value = "SELECT farmer_image_data FROM farmer WHERE farmer_image_path = :path AND farmer_image_data IS NOT NULL LIMIT 1", nativeQuery = true)
	byte[] findFarmerImageData(@Param("path") String path);

	@Modifying
	@Query(value = "UPDATE farmer SET farmer_image_data = :data WHERE vendor_id = :vendorId", nativeQuery = true)
	void updateFarmerImageData(@Param("vendorId") Integer vendorId, @Param("data") byte[] data);

	@Query(value = "SELECT farmer_image_path FROM farmer WHERE farmer_image_path IS NOT NULL", nativeQuery = true)
	List<String> findAllFarmerImagePaths();
}
