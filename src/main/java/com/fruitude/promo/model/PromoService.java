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

	// 活動或活動商品異動時，要讓商品列表的快取重新載入，前台價格才會馬上跟著變
	@Autowired
	private com.fruitude.product.model.FrontCatalogService frontCatalogService;

	public List<PromoProject> findAllByStartDateDesc() {
		return promoRepository.findAllByStartDateDesc();
	}

	// 查某個活動底下的活動商品（promotion）
	public List<Promotion> findPromotionsByProjectId(Integer promoProjectId) {
		return promotionRepository.findByPromoProjectIdOrderByPromotionId(promoProjectId);
	}

	// 後台活動詳細頁用：活動商品連同商品名稱、規格名稱與原價
	public List<PromotionRepository.PromotionRow> findPromotionRows(Integer promoProjectId) {
		return promotionRepository.findRowsByProject(promoProjectId);
	}

	// 後台「新增商品」可挑選的規格（排除這個活動已經有的）
	public List<PromotionRepository.SkuOption> findSkuOptions(Integer promoProjectId, String keyword) {
		return promotionRepository.findSkuOptions(promoProjectId, keyword == null ? "" : keyword.trim());
	}

	// 一次幫多個規格設定這個活動的促銷價（skuIds 與 promoPrices 一一對應）。
	// 全部檢查通過才會存；活動裡已經有的規格就更新促銷價。有問題丟 IllegalArgumentException，訊息就是要顯示給使用者的文字
	@Transactional
	public void saveItems(Integer promoProjectId, List<Integer> skuIds, List<Integer> promoPrices) {
		PromoProject project = promoRepository.findById(promoProjectId).orElse(null);
		if (project == null) {
			throw new IllegalArgumentException("找不到這筆活動");
		}
		if (!PromoType.SKU.name().equals(project.getPromoType())) {
			throw new IllegalArgumentException("只有活動類型為「指定商品」的活動才能設定活動商品，請先按「修改」設定活動類型");
		}
		if (skuIds == null || promoPrices == null || skuIds.isEmpty() || skuIds.size() != promoPrices.size()) {
			throw new IllegalArgumentException("請至少選一個商品，並填寫每個商品的活動價");
		}
		// 先全部檢查，沒問題才寫入，避免只存一半
		for (int i = 0; i < skuIds.size(); i++) {
			checkPromoPrice(skuIds.get(i), promoPrices.get(i));
		}
		for (int i = 0; i < skuIds.size(); i++) {
			Promotion item = promotionRepository.findByPromoProjectIdAndSkuId(promoProjectId, skuIds.get(i))
					.orElse(null);
			if (item == null) {
				item = new Promotion();
				item.setPromoProjectId(promoProjectId);
				item.setSkuId(skuIds.get(i));
			}
			item.setPromoPrice(promoPrices.get(i));
			promotionRepository.save(item);
		}
		frontCatalogService.clearCache(); // 活動價改變，商品列表快取要重新載入
	}

	// 修改一筆活動商品的促銷價
	@Transactional
	public void updateItemPrice(Integer promotionId, Integer promoPrice) {
		Promotion item = promotionRepository.findById(promotionId).orElse(null);
		if (item == null) {
			throw new IllegalArgumentException("找不到這筆活動商品");
		}
		checkPromoPrice(item.getSkuId(), promoPrice);
		item.setPromoPrice(promoPrice);
		promotionRepository.save(item);
		frontCatalogService.clearCache();
	}

	// 刪除一筆活動商品；找不到回傳 false
	@Transactional
	public boolean deleteItem(Integer promotionId) {
		if (!promotionRepository.existsById(promotionId)) {
			return false;
		}
		promotionRepository.deleteById(promotionId);
		frontCatalogService.clearCache();
		return true;
	}

	// 促銷價必須是正整數，而且要低於該規格目前的原價（否則前台不會採用，設了也沒有效果）
	private void checkPromoPrice(Integer skuId, Integer promoPrice) {
		if (skuId == null) {
			throw new IllegalArgumentException("商品規格不正確");
		}
		Integer price = promotionRepository.findSkuPrice(skuId);
		if (price == null) {
			throw new IllegalArgumentException("找不到規格 " + skuId);
		}
		if (promoPrice == null || promoPrice < 1) {
			throw new IllegalArgumentException("規格 " + skuId + " 的活動價必須大於 0");
		}
		if (promoPrice >= price) {
			throw new IllegalArgumentException("規格 " + skuId + " 的活動價 " + promoPrice + " 必須低於原價 " + price);
		}
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

	// 商品折扣：全館折扣、壽星月、新會員首購這幾種「改商品價格」的活動，同一筆訂單只套用折扣金額最大的一個
	// （不疊加），並回傳是哪個活動給的。壽星月要會員目前在生日月、新會員首購要是第一筆訂單，
	// 這兩個條件由呼叫端判斷後傳進來。折扣不會超過商品金額；沒有符合的活動回傳 ProductDiscount.NONE
	public ProductDiscount calcProductDiscount(boolean isBirthdayMonth, boolean isFirstOrder, int productTotal) {
		LocalDateTime now = LocalDateTime.now();
		ProductDiscount best = bestDiscount(promoRepository.findActiveByType(PromoType.STOREWIDE.name(), now),
				productTotal);
		if (isBirthdayMonth) {
			ProductDiscount d = bestDiscount(
					promoRepository.findActiveByType(PromoType.BIRTHDAY_MONTH.name(), now), productTotal);
			if (d.amount() > best.amount()) {
				best = d;
			}
		}
		if (isFirstOrder) {
			ProductDiscount d = bestDiscount(
					promoRepository.findActiveByType(PromoType.NEW_MEMBER_FIRST_ORDER.name(), now), productTotal);
			if (d.amount() > best.amount()) {
				best = d;
			}
		}
		if (best.amount() > productTotal) {
			return new ProductDiscount(productTotal, best.title());
		}
		return best;
	}

	// 一組活動裡，對這個商品金額折扣金額最大的一個（折扣率或折抵金額）及它的標題；沒達該活動最低消費的不算
	private ProductDiscount bestDiscount(List<PromoProject> actives, int productTotal) {
		ProductDiscount best = ProductDiscount.NONE;
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
			if (discount > best.amount()) {
				String title = p.getPromoProjectTitle();
				best = new ProductDiscount(discount, title == null ? "" : title);
			}
		}
		return best;
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
		frontCatalogService.clearCache(); // 活動期間、類型改變會影響前台價格
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
		frontCatalogService.clearCache();
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
		frontCatalogService.clearCache(); // 狀態改變會影響前台價格
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
