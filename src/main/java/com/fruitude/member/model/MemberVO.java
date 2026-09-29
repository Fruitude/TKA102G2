package com.fruitude.member.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * 會員資料實體：這個類別的每一個物件，會對應 member 資料表中的一筆資料。
 */
@Entity
@Table(name = "member")
public class MemberVO implements Serializable {

	private static final long serialVersionUID = 1L;

	// 主鍵由 MySQL 的 AUTO_INCREMENT 自動產生，新增會員時不需要自行指定。
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Integer memberId;

	@NotBlank(message = "會員姓名不可空白")
	@Size(max = 50, message = "會員姓名最多 50 個字")
	@Column(name = "member_name", length = 50, nullable = false)
	private String memberName;

	@PastOrPresent(message = "生日不可晚於今天")
	@Column(name = "member_birthday")
	private LocalDate memberBirthday;

	@NotBlank(message = "會員帳號不可空白")
	@Size(max = 50, message = "會員帳號最多 50 個字")
	@Column(name = "member_account", length = 50, nullable = false)
	private String memberAccount;

	@NotBlank(message = "電子郵件不可空白")
	@Email(message = "電子郵件格式不正確")
	@Size(max = 100, message = "電子郵件最多 100 個字")
	@Column(name = "member_email", length = 100, nullable = false)
	private String memberEmail;

	@NotBlank(message = "密碼不可空白")
	@Size(min = 8, max = 255, message = "密碼至少 8 個字")
	@Column(name = "member_password", length = 255, nullable = false)
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // 可接收密碼，但轉成 JSON 時不回傳密碼。
	private String memberPassword;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	// 1 代表啟用、0 代表停權；實際判斷集中在 Service，避免 Controller 重複邏輯。
	@Column(name = "member_status", nullable = false)
	private Integer memberStatus;

	@Column(name = "shopping_credit", nullable = false)
	private Integer shoppingCredit;

	/** 新增資料前補上系統預設值，避免建立時間、狀態或購物金出現 null。 */
	@PrePersist
	public void applyDefaults() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (memberStatus == null) {
			memberStatus = 1;
		}
		if (shoppingCredit == null) {
			shoppingCredit = 0;
		}
	}

	public Integer getMemberId() {
		return memberId;
	}

	public void setMemberId(Integer memberId) {
		this.memberId = memberId;
	}

	public String getMemberName() {
		return memberName;
	}

	public void setMemberName(String memberName) {
		this.memberName = memberName;
	}

	public LocalDate getMemberBirthday() {
		return memberBirthday;
	}

	public void setMemberBirthday(LocalDate memberBirthday) {
		this.memberBirthday = memberBirthday;
	}

	public String getMemberAccount() {
		return memberAccount;
	}

	public void setMemberAccount(String memberAccount) {
		this.memberAccount = memberAccount;
	}

	public String getMemberEmail() {
		return memberEmail;
	}

	public void setMemberEmail(String memberEmail) {
		this.memberEmail = memberEmail;
	}

	public String getMemberPassword() {
		return memberPassword;
	}

	public void setMemberPassword(String memberPassword) {
		this.memberPassword = memberPassword;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public Integer getMemberStatus() {
		return memberStatus;
	}

	public void setMemberStatus(Integer memberStatus) {
		this.memberStatus = memberStatus;
	}

	public Integer getShoppingCredit() {
		return shoppingCredit;
	}

	public void setShoppingCredit(Integer shoppingCredit) {
		this.shoppingCredit = shoppingCredit;
	}
}
