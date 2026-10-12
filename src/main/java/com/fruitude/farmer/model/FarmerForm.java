package com.fruitude.farmer.model;

import jakarta.validation.constraints.Size;

/**
 * 後台小農表單。只收可由表單修改的欄位，圖片路徑與供應商欄位不接受表單帶來的值。
 */
public class FarmerForm {

	private Integer vendorId;

	private Boolean showInFarmers = false;

	@Size(max = 50, message = "品名，字數過多")
	private String growProduct;

	@Size(max = 100, message = "標語，字數過多")
	private String farmerSlogan;

	@Size(max = 5000, message = "小農故事，字數過多")
	private String farmerStory;

	public Integer getVendorId() {
		return vendorId;
	}

	public void setVendorId(Integer vendorId) {
		this.vendorId = vendorId;
	}

	public Boolean getShowInFarmers() {
		return showInFarmers;
	}

	public void setShowInFarmers(Boolean showInFarmers) {
		this.showInFarmers = showInFarmers != null && showInFarmers;
	}

	public String getGrowProduct() {
		return growProduct;
	}

	public void setGrowProduct(String growProduct) {
		this.growProduct = growProduct;
	}

	public String getFarmerSlogan() {
		return farmerSlogan;
	}

	public void setFarmerSlogan(String farmerSlogan) {
		this.farmerSlogan = farmerSlogan;
	}

	public String getFarmerStory() {
		return farmerStory;
	}

	public void setFarmerStory(String farmerStory) {
		this.farmerStory = farmerStory;
	}
}
