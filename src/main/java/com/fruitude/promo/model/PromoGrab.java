package com.fruitude.promo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 搶購物金的參加紀錄：每位會員每場活動只有一筆（沒搶到也留一筆，所以不能重按）。
 * slotNo 是得標序號（1..名額），沒搶到為 null；同一場活動的 slotNo 不會重複。
 */
@Entity
@Table(name = "promo_grab", uniqueConstraints = {
		@UniqueConstraint(name = "uk_promo_grab_member", columnNames = { "promo_project_id", "member_id" }),
		@UniqueConstraint(name = "uk_promo_grab_slot", columnNames = { "promo_project_id", "slot_no" })
})
public class PromoGrab {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "grab_id")
	private Integer grabId;

	@Column(name = "promo_project_id", nullable = false)
	private Integer promoProjectId;

	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	@Column(name = "slot_no")
	private Integer slotNo;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected PromoGrab() {
	}

	public PromoGrab(Integer promoProjectId, Integer memberId) {
		this.promoProjectId = promoProjectId;
		this.memberId = memberId;
	}

	@PrePersist
	public void applyCreatedAt() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public Integer getGrabId() { return grabId; }
	public Integer getPromoProjectId() { return promoProjectId; }
	public Integer getMemberId() { return memberId; }
	public Integer getSlotNo() { return slotNo; }
	public void setSlotNo(Integer slotNo) { this.slotNo = slotNo; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
