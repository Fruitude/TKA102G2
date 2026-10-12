package com.fruitude.farmer.model;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.vendor.model.VendorVO;

@Service
public class FarmerService {

	@Autowired
	private FarmerRepository repository;

	// 前台小農資訊頁：勾選「顯示在小農資訊」且供應商啟用中的小農
	public List<FarmerVO> getFarmersForFront() {
		return repository.findForFront((byte) 1);
	}

	public List<FarmerVO> getAll() {
		return repository.findAllWithVendor();
	}

	public FarmerVO getOne(Integer vendorId) {
		return repository.findById(vendorId).orElse(null);
	}

	public boolean exists(Integer vendorId) {
		return repository.existsById(vendorId);
	}

	// 新增小農的供應商下拉選單：全部已啟用的供應商，hasFarmer 標示是否已經有小農資料（已有的不可再選）
	public List<Map<String, Object>> getVendorOptions() {
		java.util.Set<Integer> used = new java.util.HashSet<>(repository.findAllVendorIds());
		List<Map<String, Object>> options = new java.util.ArrayList<>();
		for (Map<String, Object> option : repository.findVendorOptions((byte) 1)) {
			Map<String, Object> copy = new java.util.HashMap<>(option);
			copy.put("hasFarmer", used.contains(option.get("vendorId")));
			options.add(copy);
		}
		return options;
	}

	// 新增：主鍵由供應商帶出（@MapsId），不先設定 vendorId 讓 Spring Data 視為新資料
	public void add(VendorVO vendor, FarmerForm form, String imagePath) {
		FarmerVO farmer = new FarmerVO();
		farmer.setVendor(vendor);
		apply(farmer, form);
		farmer.setFarmerImagePath(imagePath);
		repository.save(farmer);
	}

	// 修改：imagePath 為 null 表示沒有換圖，維持原本的圖片
	public void update(FarmerVO farmer, FarmerForm form, String imagePath) {
		apply(farmer, form);
		if (imagePath != null) {
			farmer.setFarmerImagePath(imagePath);
		}
		repository.save(farmer);
	}

	// 小農圖片的二進位內容（資料夾沒有檔案時用來補檔）；不放在 FarmerVO 裡以免每次查詢都讀出
	@Transactional
	public void saveFarmerImageData(Integer vendorId, byte[] data) {
		repository.updateFarmerImageData(vendorId, data);
	}

	private void apply(FarmerVO farmer, FarmerForm form) {
		farmer.setShowInFarmers(form.getShowInFarmers());
		farmer.setGrowProduct(form.getGrowProduct());
		farmer.setFarmerSlogan(form.getFarmerSlogan());
		farmer.setFarmerStory(form.getFarmerStory());
	}
}
