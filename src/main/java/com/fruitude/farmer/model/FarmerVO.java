package com.fruitude.farmer.model;

import com.fruitude.vendor.model.VendorVO;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * 前台「小農資訊」頁的小農資料。與供應商一對一（主鍵同時是 vendor_id），
 * 姓名、產地、介紹等供應商欄位不重複存放，一律由 vendor 讀取。
 * 圖片的二進位內容（farmer_image_data）不放在這裡，由 FarmerRepository 另外讀寫。
 */
@Entity
@Table(name = "farmer")
public class FarmerVO implements java.io.Serializable {

	@Id
	@Column(name = "vendor_id", updatable = false)
	private Integer vendorId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "vendor_id")
	private VendorVO vendor;

	// 是否顯示在前台「小農資訊」頁
	@Column(name = "show_in_farmers", nullable = false)
	private Boolean showInFarmers = false;

	// 小農圖片的相對路徑（自 templates 起算），例如 /front/img/farmers/001.png；由上傳功能寫入
	@Column(name = "farmer_image_path", length = 255)
	private String farmerImagePath;

	@Column(name = "grow_product", length = 50)
	private String growProduct;

	@Column(name = "farmer_slogan", length = 100)
	private String farmerSlogan;

	@Column(name = "farmer_story", columnDefinition = "TEXT")
	private String farmerStory;

	public FarmerVO() {
	}

	public Integer getVendorId() {
		return vendorId;
	}

	public void setVendorId(Integer vendorId) {
		this.vendorId = vendorId;
	}

	public VendorVO getVendor() {
		return vendor;
	}

	public void setVendor(VendorVO vendor) {
		this.vendor = vendor;
	}

	public Boolean getShowInFarmers() {
		return showInFarmers;
	}

	public void setShowInFarmers(Boolean showInFarmers) {
		this.showInFarmers = showInFarmers != null && showInFarmers;
	}

	public String getFarmerImagePath() {
		return farmerImagePath;
	}

	public void setFarmerImagePath(String farmerImagePath) {
		this.farmerImagePath = farmerImagePath;
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
