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

	// 搶購物金的參加紀錄（promo_grab），刪活動時要一併刪除
	@Autowired
	private PromoGrabRepository promoGrabRepository;

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

	// 目前進行中的壽星月活動（有優惠值的第一筆）；沒有回傳 null。結帳確認頁用它決定要不要顯示「使用壽星優惠」勾選框
	public PromoProject findActiveBirthdayPromo() {
		for (PromoProject p : promoRepository.findActiveByType(PromoType.BIRTHDAY_MONTH.name(), LocalDateTime.now())) {
			if (p.getBenefitValue() != null && p.getBenefitValue() >= 1) {
				return p;
			}
		}
		return null;
	}

	// 首頁的活動區塊：目前進行中的活動分成「限時活動」與「會員專屬福利（常態）」兩組。
	// 只放已經能實際生效的類型（PromoType.isShownOnFront）。
	// 會員個人化：已登入且生日在本月的會員，「壽星月」會標示為目前符合資格；所有活動（含新會員首購）一律顯示，
	// 不因會員身分隱藏。生日月份由呼叫端依登入會員自己算出來，不接受前端傳入
	public HomePromos findHomePromos(MemberPromoState state) {
		return buildFrontPromos(state, false);
	}

	// 活動總覽頁：同樣分組與個人化，但每個活動多帶完整說明、名額，指定商品活動還帶商品與活動價
	public HomePromos findPromotionsPage(MemberPromoState state) {
		return buildFrontPromos(state, true);
	}

	private HomePromos buildFrontPromos(MemberPromoState state, boolean withDetails) {
		List<HomePromos.Item> limited = new ArrayList<>();
		List<HomePromos.Item> perks = new ArrayList<>();
		for (PromoProject p : promoRepository.findActive(LocalDateTime.now())) {
			PromoType type = PromoType.of(p.getPromoType());
			if (type == null || !type.isShownOnFront()) {
				continue;
			}
			// 壽星月：今年已經用過就標示「已使用」；還沒用過而且現在是生日月才標示「本月適用」。
			// 新會員首購：已經有有效訂單就標示「已使用」；還沒有就標示「首購適用」。訪客都不標示
			boolean used = false;
			boolean highlight = false;
			String highlightText = "";
			if (state.loggedIn() && type == PromoType.BIRTHDAY_MONTH) {
				used = state.birthdayUsed();
				highlight = state.birthdayMonth() && !used;
				highlightText = "本月適用";
			} else if (state.loggedIn() && type == PromoType.NEW_MEMBER_FIRST_ORDER) {
				used = state.firstPurchaseUsed();
				highlight = !used;
				highlightText = "首購適用";
			}
			// 起訖日兩組都帶出；常態福利在首頁不顯示日期與倒數（見 promo-section.html），活動總覽頁才顯示舉辦日期
			LocalDateTime start = p.getPromoProjectStart();
			LocalDateTime end = p.getPromoProjectEnd();
			String context = null;
			Integer quota = null;
			List<HomePromos.Product> products = new ArrayList<>();
			if (withDetails) {
				context = p.getPromoProjectContext();
				quota = p.getQuota();
				if (type == PromoType.SKU) {
					for (PromotionRepository.PromotionRow row : promotionRepository.findRowsByProject(p.getPromoProjectId())) {
						products.add(new HomePromos.Product(row.getProductId(), row.getProductName(), row.getSkuName(),
								row.getOriginalPrice(), row.getPromoPrice()));
					}
				}
			}
			HomePromos.Item item = new HomePromos.Item(p.getPromoProjectId(), p.getPromoProjectTitle(),
					type.getLabel(), homeBenefitText(type, p), homeConditionText(type, p), start, end, highlight, highlightText, used,
					context, quota, products);
			if (type.isRecurring()) {
				perks.add(item);
			} else {
				limited.add(item);
			}
		}
		return new HomePromos(limited, perks);
	}

	// 優惠內容文字：指定商品的價格在活動商品上，寫成「指定商品優惠價」；其餘用優惠方式加優惠值（例如「9 折」）
	private String homeBenefitText(PromoType type, PromoProject p) {
		if (type == PromoType.SKU) {
			return "指定商品優惠價";
		}
		return BenefitType.describe(p.getBenefitType(), p.getBenefitValue());
	}

	// 使用資格文字，讓會員看得出自己能不能用
	private String homeConditionText(PromoType type, PromoProject p) {
		String who = switch (type) {
			case NEW_MEMBER_FIRST_ORDER -> "首次下單，限用一次（訂單取消或退款後可再使用）";
			case BIRTHDAY_MONTH -> "生日當月，每年限用一次，結帳時勾選使用";
			default -> "";
		};
		Integer min = p.getMinOrderAmount();
		String amount = (min != null && min > 0) ? "訂單滿 " + min + " 元" : "";
		if (who.isEmpty()) {
			return amount;
		}
		return amount.isEmpty() ? who : who + "，" + amount;
	}

	// 商品折扣：全館折扣、壽星月、新會員首購，和指定商品活動價「擇優」，並回傳是哪個活動給的。
	// 這三種折扣活動彼此只取折扣最大的一個（不疊加）；折扣金額是「相對於畫面上小計（已經是活動價）」再多折的金額。
	// birthdayChosen：會員有資格（生日月、今年還沒用過）而且這次結帳勾選了要用壽星優惠，才會讓壽星月參與比較；
	// 就算勾選了，如果其他折扣更划算，最後也不會套用壽星月（呼叫端看回傳的 promoType 判斷有沒有真的用到）。
	// 新會員首購要是第一筆訂單，這個條件由呼叫端判斷後傳進來。沒有符合的活動回傳 ProductDiscount.NONE
	public ProductDiscount calcProductDiscount(boolean birthdayChosen, boolean isFirstOrder, List<DiscountLine> lines) {
		long displayedTotal = 0;
		for (DiscountLine line : lines) {
			displayedTotal += line.lineTotal();
		}
		LocalDateTime now = LocalDateTime.now();
		List<PromoProject> candidates = new ArrayList<>(promoRepository.findActiveByType(PromoType.STOREWIDE.name(), now));
		if (birthdayChosen) {
			candidates.addAll(promoRepository.findActiveByType(PromoType.BIRTHDAY_MONTH.name(), now));
		}
		if (isFirstOrder) {
			candidates.addAll(promoRepository.findActiveByType(PromoType.NEW_MEMBER_FIRST_ORDER.name(), now));
		}
		ProductDiscount best = ProductDiscount.NONE;
		for (PromoProject p : candidates) {
			Integer min = p.getMinOrderAmount();
			Integer value = p.getBenefitValue();
			if ((min != null && displayedTotal < min) || value == null || value < 1) {
				continue;
			}
			long extra = extraDiscount(p, value, lines);
			if (extra > displayedTotal) {
				extra = displayedTotal;
			}
			if (extra > best.amount()) {
				String title = p.getPromoProjectTitle();
				best = new ProductDiscount((int) extra, title == null ? "" : title, p.getPromoProjectId(), p.getPromoType());
			}
		}
		return best;
	}

	// 套用某個折扣活動，比起「畫面上的小計（已經是活動價）」可以再多折多少：
	// - 折扣率：每個品項各自比較「指定商品活動價」和「原價打折」，取比較便宜的；沒有活動價的品項就是原價打折
	// - 折抵金額：是整筆訂單一個固定金額，沒辦法拆到每個品項，所以比較兩種整筆結果取較划算的：
	//   (A) 有活動價的品項維持活動價，折抵金額只用在沒有活動價的品項；(B) 全部品項用原價，折抵整筆原價
	private long extraDiscount(PromoProject p, int value, List<DiscountLine> lines) {
		if (BenefitType.PERCENT_OFF.name().equals(p.getBenefitType())) {
			long extra = 0;
			for (DiscountLine line : lines) {
				long memberSaving = line.originalLineTotal() * (100 - value) / 100;
				extra += Math.max(0, memberSaving - line.promoSaving());
			}
			return extra;
		}
		if (BenefitType.AMOUNT_OFF.name().equals(p.getBenefitType())) {
			long nonPromoTotal = 0;
			long originalTotal = 0;
			long promoSavingTotal = 0;
			for (DiscountLine line : lines) {
				originalTotal += line.originalLineTotal();
				promoSavingTotal += line.promoSaving();
				if (line.promoSaving() == 0) {
					nonPromoTotal += line.lineTotal();
				}
			}
			long optionA = Math.min(value, nonPromoTotal);
			long optionB = Math.max(0, Math.min(value, originalTotal) - promoSavingTotal);
			return Math.max(optionA, optionB);
		}
		return 0;
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
		promoGrabRepository.deleteByPromoProjectId(id);
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
