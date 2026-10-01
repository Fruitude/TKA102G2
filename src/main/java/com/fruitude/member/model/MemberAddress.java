package com.fruitude.member.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 會員地址實體：每一個物件對應 address 資料表中的一筆收件地址。
 */
@Entity
@Table(name = "address")
public class MemberAddress {

	// 地址編號由資料庫自動產生，前端不能自行指定。
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "address_id")
	private Integer addressId;

	// 用會員編號區分資料擁有者；查詢、修改及刪除時都會一併檢查。
	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	@Column(name = "contact_address", length = 255, nullable = false)
	private String contactAddress;

	// 資料庫以 1/0 保存是否為預設地址。
	@Column(name = "is_default", nullable = false, columnDefinition = "TINYINT")
	private Byte defaultValue;

	public Integer getAddressId() {
		return addressId;
	}

	public void setAddressId(Integer addressId) {
		this.addressId = addressId;
	}

	public Integer getMemberId() {
		return memberId;
	}

	public void setMemberId(Integer memberId) {
		this.memberId = memberId;
	}

	public String getContactAddress() {
		return contactAddress;
	}

	public void setContactAddress(String contactAddress) {
		this.contactAddress = contactAddress;
	}

	public Byte getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(Byte defaultValue) {
		this.defaultValue = defaultValue;
	}
}
