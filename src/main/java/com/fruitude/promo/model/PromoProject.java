package com.fruitude.promo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 活動專案，對應資料表 promo_project
@Entity
@Table(name = "promo_project")
public class PromoProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "promo_project_id")
    private Integer promoProjectId;

    @Column(name = "promo_project_start")
    private LocalDateTime promoProjectStart;

    @Column(name = "promo_project_end")
    private LocalDateTime promoProjectEnd;

    @Column(name = "promo_project_title", length = 50)
    private String promoProjectTitle;

    @Column(name = "promo_project_context", length = 255)
    private String promoProjectContext;

    // 活動類型：STOREWIDE、SKU、BIRTHDAY_MONTH、NEW_MEMBER_FIRST_ORDER、FREE_SHIPPING、WALLET_GRAB、REVIEW_REWARD
    @Column(name = "promo_type", length = 30)
    private String promoType;

    // 優惠方式：PERCENT_OFF、AMOUNT_OFF、FIXED_PRICE、WALLET_CREDIT、FREE_SHIPPING
    @Column(name = "benefit_type", length = 30)
    private String benefitType;

    // 優惠值：90 = 9 折、500 = 500 元，免運用 0
    @Column(name = "benefit_value")
    private Integer benefitValue;

    // 最低消費（滿額免運用），null 代表不限
    @Column(name = "min_order_amount")
    private Integer minOrderAmount;

    // 名額上限（搶購物金用），null 代表不限
    @Column(name = "quota")
    private Integer quota;

    // 狀態：1 = 啟用、0 = 停用
    @Column(name = "status")
    private Integer status;

    // 已搶出的名額（搶購物金用）；只由 PromoRepository.claimSlot 原子 +1，不要用 setter 改
    @Column(name = "granted_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer grantedCount = 0;

	public Integer getGrantedCount() {
		return grantedCount == null ? 0 : grantedCount;
	}

	public String getPromoType() {
		return promoType;
	}

	public void setPromoType(String promoType) {
		this.promoType = promoType;
	}

	public String getBenefitType() {
		return benefitType;
	}

	public void setBenefitType(String benefitType) {
		this.benefitType = benefitType;
	}

	public Integer getBenefitValue() {
		return benefitValue;
	}

	public void setBenefitValue(Integer benefitValue) {
		this.benefitValue = benefitValue;
	}

	public Integer getMinOrderAmount() {
		return minOrderAmount;
	}

	public void setMinOrderAmount(Integer minOrderAmount) {
		this.minOrderAmount = minOrderAmount;
	}

	public Integer getQuota() {
		return quota;
	}

	public void setQuota(Integer quota) {
		this.quota = quota;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public Integer getPromoProjectId() {
		return promoProjectId;
	}

	public void setPromoProjectId(Integer promoProjectId) {
		this.promoProjectId = promoProjectId;
	}

	public LocalDateTime getPromoProjectStart() {
		return promoProjectStart;
	}

	public void setPromoProjectStart(LocalDateTime promoProjectStart) {
		this.promoProjectStart = promoProjectStart;
	}

	public LocalDateTime getPromoProjectEnd() {
		return promoProjectEnd;
	}

	public void setPromoProjectEnd(LocalDateTime promoProjectEnd) {
		this.promoProjectEnd = promoProjectEnd;
	}

	public String getPromoProjectTitle() {
		return promoProjectTitle;
	}

	public void setPromoProjectTitle(String promoProjectTitle) {
		this.promoProjectTitle = promoProjectTitle;
	}

	public String getPromoProjectContext() {
		return promoProjectContext;
	}

	public void setPromoProjectContext(String promoProjectContext) {
		this.promoProjectContext = promoProjectContext;
	}
}
