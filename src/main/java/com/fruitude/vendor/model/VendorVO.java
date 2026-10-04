package com.fruitude.vendor.model;

import java.util.ArrayList;
import java.util.List;
import com.fruitude.po.model.PoVO;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;


@Entity
@Table(name = "vendor")
public class VendorVO implements java.io.Serializable{
	
	@Id
	@Column(name = "vendor_id",updatable = false)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer vendorId;

	@Column(name = "vendor_name", nullable = false, length = 30)
	@Size(max = 30, message = "供應商名稱，字數過多")
	@NotBlank(message = "供應商名稱，請勿空白")
	private String vendorName;

	@Column(name = "vendor_introduction", columnDefinition = "TEXT")
	@Size(max = 150, message = "供應商介紹，字數過多")
	private String vendorIntroduction;

	@NotBlank(message = "產地，請勿空白")
	@Size(max = 20, message = "產地，字數過多")
	@Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z]+$", message = "產地，只能是中、英文字母，不可有空白")
	@Column(name = "origin", nullable = false, length = 20)
	private String origin;

	@Column(name = "logo", columnDefinition = "LONGBLOB")
	private byte[] logo;

	@NotBlank(message = "聯絡人姓名，請勿空白")
	@Size(max = 10, message = "聯絡人姓名字數過多")
	@Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z]+( [\\u4e00-\\u9fa5a-zA-Z]+)*$",
	message = "聯絡人姓名，只能是中、英文字母，字元間可以有一個空白")
	@Column(name = "contact_person", nullable = false, length = 10)
	private String contactPerson;

	@Pattern(regexp = "\\d{8}", message = "統一編號，必須為數字，長度必須為8碼")
	@Column(name = "tax_id", length = 8, unique = true)
	private String taxId;

	@NotBlank(message = "聯絡電話，請勿空白")
	@Pattern(regexp = "^(09\\d{2}-\\d{6}|0[2-8]\\d{0,2}-\\d{5,8})$",
	message = "連絡電話必須以0開頭，格式為09xx-xxxxxx或0x-xxxxxxx")
	@Column(name = "phone", nullable = false, length = 20)
	private String phone;

	@Email(message = "Email格式不正確")
	@Size(max = 100, message = "Email字數過多")
	@Column(name = "email", length = 100)
	private String email;

	@NotNull(message = "啟用狀態，請勿空白")
	@Min(value = 0, message = "啟用狀態，值不正確")
	@Max(value = 2, message = "啟用狀態，值不正確")
	@Column(name = "is_active", nullable = false)
	private Byte isActive = 0;

	@OneToMany(mappedBy = "vendor", fetch = FetchType.LAZY)
	private List<PoVO> pos = new ArrayList<>();


	public VendorVO() {
		
	}
	public Integer getVendorId() {
		return vendorId;
	}
	public void setVendorId(Integer vendorId) {
		this.vendorId = vendorId;
	}
	public String getVendorName() {
		return vendorName;
	}
	public void setVendorName(String vendorName) {
		this.vendorName = vendorName;
	}
	public String getVendorIntroduction() {
		return vendorIntroduction;
	}
	public void setVendorIntroduction(String vendorIntroduction) {
		this.vendorIntroduction = vendorIntroduction;
	}
	public String getOrigin() {
		return origin;
	}
	public void setOrigin(String origin) {
		this.origin = origin;
	}
	public byte[] getLogo() {
		return logo;
	}
	public void setLogo(byte[] logo) {
		this.logo = logo;
	}
	public String getContactPerson() {
		return contactPerson;
	}
	public void setContactPerson(String contactPerson) {
		this.contactPerson = contactPerson;
	}
	public String getTaxId() {
		return taxId;
	}
	public void setTaxId(String taxId) {
		this.taxId = taxId;
	}
	public String getPhone() {
		return phone;
	}
	public void setPhone(String phone) {
		this.phone = phone;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public Byte getIsActive() {
		return isActive;
	}
	public void setIsActive(Byte isActive) {
		this.isActive = isActive;
	}
	public List<PoVO> getPos() {
		return pos;
	}
	public void setPos(List<PoVO> pos) {
		this.pos = pos;
	}
	
	

}
