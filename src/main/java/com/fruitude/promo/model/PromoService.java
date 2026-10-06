package com.fruitude.promo.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

	// 更新活動的標題、內容、開始與結束時間；找不到這筆活動回傳 false
	public boolean update(Integer id, String title, String context, LocalDateTime start, LocalDateTime end) {
		PromoProject promo = promoRepository.findById(id).orElse(null);
		if (promo == null) {
			return false;
		}
		promo.setPromoProjectTitle(title);
		promo.setPromoProjectContext(context);
		promo.setPromoProjectStart(start);
		promo.setPromoProjectEnd(end);
		promoRepository.save(promo);
		return true;
	}
}
