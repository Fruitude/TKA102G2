package com.fruitude.member.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 會員電話實體：每一個物件對應 phone 資料表中的一筆聯絡電話。
 */
@Entity
@Table(name = "phone")
public class MemberPhone {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "phone_id")
	private Integer phoneId;

	// 會員編號由登入 session 決定，不採用前端傳入的值。
	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	@Column(name = "contact_phone", length = 20, nullable = false)
	private String contactPhone;

	@Column(name = "is_default", nullable = false, columnDefinition = "TINYINT")
	private Byte defaultValue;

	public Integer getPhoneId() {
		return phoneId;
	}

	public void setPhoneId(Integer phoneId) {
		this.phoneId = phoneId;
	}

	public Integer getMemberId() {
		return memberId;
	}

	public void setMemberId(Integer memberId) {
		this.memberId = memberId;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public void setContactPhone(String contactPhone) {
		this.contactPhone = contactPhone;
	}

	public Byte getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(Byte defaultValue) {
		this.defaultValue = defaultValue;
	}
}
