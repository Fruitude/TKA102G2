package com.fruitude.member.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 會員信用卡實體：只把可辨識卡片的安全資訊回傳前端，不保存或顯示完整卡號。
 */
@Entity
@Table(name = "credit_card")
public class MemberCreditCard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "credit_card_id")
	private Integer creditCardId;

	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	// token 由伺服器產生，標示 JsonIgnore 可避免任何 API 回傳這個敏感欄位。
	@JsonIgnore
	@Column(name = "card_token", length = 255, nullable = false, unique = true)
	private String cardToken;

	@Column(name = "card_brand", length = 20, nullable = false)
	private String cardBrand;

	@Column(name = "card_last_four", length = 4, nullable = false)
	private String cardLastFour;

	@Column(name = "cardholder_name", length = 50, nullable = false)
	private String cardholderName;

	// 資料庫原有格式為 YYMM，例如 2029 年 12 月保存為 2912。
	@Column(name = "expiry_year_month", length = 4, nullable = false)
	private String expiryYearMonth;

	@Column(name = "is_default", nullable = false, columnDefinition = "TINYINT")
	private Byte defaultValue;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/** 新增信用卡資料時，自動補上建立時間。 */
	@PrePersist
	public void applyCreatedAt() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public Integer getCreditCardId() {
		return creditCardId;
	}

	public void setCreditCardId(Integer creditCardId) {
		this.creditCardId = creditCardId;
	}

	public Integer getMemberId() {
		return memberId;
	}

	public void setMemberId(Integer memberId) {
		this.memberId = memberId;
	}

	public String getCardToken() {
		return cardToken;
	}

	public void setCardToken(String cardToken) {
		this.cardToken = cardToken;
	}

	public String getCardBrand() {
		return cardBrand;
	}

	public void setCardBrand(String cardBrand) {
		this.cardBrand = cardBrand;
	}

	public String getCardLastFour() {
		return cardLastFour;
	}

	public void setCardLastFour(String cardLastFour) {
		this.cardLastFour = cardLastFour;
	}

	public String getCardholderName() {
		return cardholderName;
	}

	public void setCardholderName(String cardholderName) {
		this.cardholderName = cardholderName;
	}

	public String getExpiryYearMonth() {
		return expiryYearMonth;
	}

	public void setExpiryYearMonth(String expiryYearMonth) {
		this.expiryYearMonth = expiryYearMonth;
	}

	public Byte getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(Byte defaultValue) {
		this.defaultValue = defaultValue;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
