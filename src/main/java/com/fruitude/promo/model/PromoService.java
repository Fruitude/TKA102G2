package com.fruitude.promo.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromoService {
	@Autowired
	private PromoRepository promoRepository;

	@Autowired
	private PromotionRepository promotionRepository;

	public List<PromoProject> findAllByStartDateDesc() {
		return promoRepository.findAllByStartDateDesc();
	}

	// 查某個活動底下的活動商品（promotion）
	public List<Promotion> findPromotionsByProjectId(Integer promoProjectId) {
		return promotionRepository.findByPromoProjectIdOrderByPromotionId(promoProjectId);
	}

	// 目前進行中的「滿額免運」活動的最低消費門檻（多個活動取最低的）；沒有進行中的活動回傳 0
	public int findFreeShippingThreshold() {
		List<PromoProject> actives = promoRepository.findActiveByType(PromoType.FREE_SHIPPING.name(),
				LocalDateTime.now());
		int threshold = 0;
		for (PromoProject p : actives) {
			Integer min = p.getMinOrderAmount();
			if (min != null && min > 0 && (threshold == 0 || min < threshold)) {
				threshold = min;
			}
		}
		return threshold;
	}

	// 「新會員首購」折扣金額：目前進行中的活動裡，取折扣金額最大的一個（折扣率或折抵金額），
	// 商品金額沒達該活動的最低消費就不算；折扣不會超過商品金額。是不是首購由呼叫端判斷
	public int calcNewMemberDiscount(int productTotal) {
		List<PromoProject> actives = promoRepository.findActiveByType(PromoType.NEW_MEMBER_FIRST_ORDER.name(),
				LocalDateTime.now());
		int best = 0;
		for (PromoProject p : actives) {
			Integer min = p.getMinOrderAmount();
			Integer value = p.getBenefitValue();
			if ((min != null && productTotal < min) || value == null || value < 1) {
				continue;
			}
			int discount = 0;
			if (BenefitType.PERCENT_OFF.name().equals(p.getBenefitType())) {
				discount = productTotal * (100 - value) / 100;
			} else if (BenefitType.AMOUNT_OFF.name().equals(p.getBenefitType())) {
				discount = value;
			}
			if (discount > best) {
				best = discount;
			}
		}
		return Math.min(best, productTotal);
	}

	// 查單筆活動；找不到回傳 null
	public PromoProject findById(Integer id) {
		return promoRepository.findById(id).orElse(null);
	}

	// 依欄位搜尋：id=活動編號（完全相符）、title/context=關鍵字包含、start/end=該日期（yyyy-MM-dd）當天。
	// 沒有關鍵字就回傳全部；編號或日期格式不正確則回傳空清單
	public List<PromoProject> search(String field, String keyword) {
		if (keyword == null || keyword.trim().isEmpty()) {
			return findAllByStartDateDesc();
		}
		String kw = keyword.trim();
		if ("id".equals(field)) {
			try {
				return promoRepository.searchById(Integer.valueOf(kw));
			} catch (NumberFormatException e) {
				return new ArrayList<>();
			}
		}
		if ("title".equals(field)) {
			return promoRepository.searchByTitle(kw);
		}
		if ("context".equals(field)) {
			return promoRepository.searchByContext(kw);
		}
		if ("start".equals(field) || "end".equals(field)) {
			LocalDate day;
			try {
				day = LocalDate.parse(kw);
			} catch (DateTimeParseException e) {
				return new ArrayList<>();
			}
			LocalDateTime from = day.atStartOfDay();
			LocalDateTime to = day.plusDays(1).atStartOfDay();
			if ("start".equals(field)) {
				return promoRepository.searchByStartBetween(from, to);
			}
			return promoRepository.searchByEndBetween(from, to);
		}
		return findAllByStartDateDesc();
	}

	// 更新活動的標題、內容、起訖時間與類型相關欄位（不動啟用／停用狀態，狀態用 updateStatus 切換）；
	// 找不到這筆活動回傳 false
	public boolean update(Integer id, String title, String context, LocalDateTime start, LocalDateTime end,
			String promoType, String benefitType, Integer benefitValue, Integer minOrderAmount, Integer quota) {
		PromoProject promo = promoRepository.findById(id).orElse(null);
		if (promo == null) {
			return false;
		}
		fillFields(promo, title, context, start, end, promoType, benefitType, benefitValue, minOrderAmount, quota);
		promoRepository.save(promo);
		return true;
	}

	// 新增活動，回傳存檔後的活動（含自動產生的編號）。新活動一律先停用，確認設定沒問題再用列表的開關啟用
	public PromoProject create(String title, String context, LocalDateTime start, LocalDateTime end,
			String promoType, String benefitType, Integer benefitValue, Integer minOrderAmount, Integer quota) {
		PromoProject promo = new PromoProject();
		fillFields(promo, title, context, start, end, promoType, benefitType, benefitValue, minOrderAmount, quota);
		promo.setStatus(0);
		return promoRepository.save(promo);
	}

	// 這個活動有幾筆購物金異動紀錄；有紀錄就不能刪除
	public long countCreditTransactions(Integer id) {
		return promoRepository.countCreditTransactions(id);
	}

	// 刪除活動，連同它底下的活動商品（promotion）一起刪；withCreditRecords 為 true 時，
	// 也一併刪除這個活動的購物金異動紀錄（member_credit_transaction，那張表的外鍵指到活動，不先刪就刪不掉活動）。
	// 全部在同一個交易，其中一個失敗就都不刪；找不到這筆活動回傳 false
	@Transactional
	public boolean delete(Integer id, boolean withCreditRecords) {
		if (!promoRepository.existsById(id)) {
			return false;
		}
		if (withCreditRecords) {
			promoRepository.deleteCreditTransactions(id);
		}
		promotionRepository.deleteByPromoProjectId(id);
		promoRepository.deleteById(id);
		return true;
	}

	// 只切換啟用／停用狀態；找不到這筆活動回傳 false
	public boolean updateStatus(Integer id, Integer status) {
		PromoProject promo = promoRepository.findById(id).orElse(null);
		if (promo == null) {
			return false;
		}
		promo.setStatus(status);
		promoRepository.save(promo);
		return true;
	}

	private void fillFields(PromoProject promo, String title, String context, LocalDateTime start,
			LocalDateTime end, String promoType, String benefitType, Integer benefitValue, Integer minOrderAmount,
			Integer quota) {
		promo.setPromoProjectTitle(title);
		promo.setPromoProjectContext(context);
		promo.setPromoProjectStart(start);
		promo.setPromoProjectEnd(end);
		promo.setPromoType(promoType);
		promo.setBenefitType(benefitType);
		promo.setBenefitValue(benefitValue);
		promo.setMinOrderAmount(minOrderAmount);
		promo.setQuota(quota);
	}
}
