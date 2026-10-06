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
