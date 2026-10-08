package com.fruitude.promo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// 會員使用「每年限用一次」活動的紀錄（目前只有壽星月），對應資料表 member_promo_usage。
// 同一個會員、同一年、同一種活動類型只能有一筆（資料庫的唯一限制，兩個視窗同時下單也只會有一個成功）。
// 訂單取消或退款時，刪除這筆紀錄就等於把今年的使用資格還給會員
@Entity
@Table(name = "member_promo_usage", uniqueConstraints = @UniqueConstraint(
		name = "uk_member_promo_usage_year", columnNames = { "member_id", "usage_year", "promo_type" }))
public class MemberPromoUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "usage_id")
	private Integer usageId;

	@Column(name = "member_id", nullable = false)
	private Integer memberId;

	// 使用的年份（以下單當天為準）
	@Column(name = "usage_year", nullable = false)
	private Integer usageYear;

	// 活動類型，存 PromoType 的 name()，例如 BIRTHDAY_MONTH
	@Column(name = "promo_type", nullable = false, length = 30)
	private String promoType;

	@Column(name = "promo_project_id")
	private Integer promoProjectId;

	// 是哪一筆訂單用掉的；訂單取消或退款時用它找到要退還的紀錄
	@Column(name = "orders_id")
	private Integer ordersId;

	public MemberPromoUsage() {
	}

	public MemberPromoUsage(Integer memberId, Integer usageYear, String promoType, Integer promoProjectId,
			Integer ordersId) {
		this.memberId = memberId;
		this.usageYear = usageYear;
		this.promoType = promoType;
		this.promoProjectId = promoProjectId;
		this.ordersId = ordersId;
	}

	public Integer getUsageId() {
		return usageId;
	}

	public Integer getMemberId() {
		return memberId;
	}

	public Integer getUsageYear() {
		return usageYear;
	}

	public String getPromoType() {
		return promoType;
	}

	public Integer getPromoProjectId() {
		return promoProjectId;
	}

	public Integer getOrdersId() {
		return ordersId;
	}
}
