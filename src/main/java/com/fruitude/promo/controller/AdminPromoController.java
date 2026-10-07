package com.fruitude.promo.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.fruitude.promo.model.BenefitType;
import com.fruitude.promo.model.PromoProject;
import com.fruitude.promo.model.PromoService;
import com.fruitude.promo.model.PromoType;
import com.fruitude.promo.model.PromotionRepository;

/**
 * 後台「活動管理」：列表（搜尋）、詳細、新增、修改、啟用／停用。
 * 對應資料表 promo_project（活動專案）與 promotion（活動商品）。
 */
@Controller
@RequestMapping("/admin/promo")
public class AdminPromoController {
	@Autowired
	private PromoService promoService;

	// 新增與修改共用：通過檢查、並依活動類型整理過的欄位（用不到的欄位一律是 null）
	private record PromoInput(String title, String context, LocalDateTime start, LocalDateTime end,
			String promoType, String benefitType, Integer benefitValue, Integer minOrderAmount, Integer quota) {
	}

	@GetMapping("/")
	public String getPromoList(@RequestParam(required = false, defaultValue = "id") String field,
			@RequestParam(required = false, defaultValue = "") String keyword, Model model) {
		List<PromoProject> promoProjects = promoService.search(field, keyword);
		model.addAttribute("promoProjects", promoProjects);
		model.addAttribute("field", field);
		model.addAttribute("keyword", keyword);
		return "admin/promo/index";
	}

	// 列表的「詳細」按鈕：顯示這個活動底下的活動商品（promotion 資料表）
	@GetMapping("/detail")
	public String getPromoDetail(@RequestParam Integer promoProjectId, Model model) {
		PromoProject promo = promoService.findById(promoProjectId);
		if (promo == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這筆活動");
		}
		model.addAttribute("promo", promo);
		model.addAttribute("promotions", promoService.findPromotionRows(promoProjectId));
		return "admin/promo/detail/index";
	}

	// 詳細頁「新增商品」對話框：可以加入這個活動的規格清單（含商品名稱與原價），可用關鍵字搜尋
	@GetMapping("/sku-options")
	@ResponseBody
	public List<PromotionRepository.SkuOption> skuOptions(@RequestParam Integer promoProjectId,
			@RequestParam(defaultValue = "") String keyword) {
		return promoService.findSkuOptions(promoProjectId, keyword);
	}

	// 一次幫多個規格設定活動價：skuIds 與 promoPrices 一一對應；已經在活動裡的規格就更新價格
	@PostMapping("/items/save")
	@ResponseBody
	public ResponseEntity<String> saveItems(@RequestParam Integer promoProjectId,
			@RequestParam List<Integer> skuIds, @RequestParam List<Integer> promoPrices) {
		try {
			promoService.saveItems(promoProjectId, skuIds, promoPrices);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
		return ResponseEntity.ok("已儲存 " + skuIds.size() + " 個商品的活動價");
	}

	// 修改一筆活動商品的活動價
	@PostMapping("/items/update")
	@ResponseBody
	public ResponseEntity<String> updateItem(@RequestParam Integer promotionId, @RequestParam Integer promoPrice) {
		try {
			promoService.updateItemPrice(promotionId, promoPrice);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
		return ResponseEntity.ok("更新成功");
	}

	// 移除一筆活動商品（只刪這個規格的活動價，不影響商品本身）
	@PostMapping("/items/delete")
	@ResponseBody
	public ResponseEntity<String> deleteItem(@RequestParam Integer promotionId) {
		if (!promoService.deleteItem(promotionId)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到這筆活動商品");
		}
		return ResponseEntity.ok("已移除");
	}

	// 對話框按「確定」時呼叫：更新這筆活動。欄位檢查在伺服器端做，不能信任瀏覽器送來的值
	@PostMapping("/update")
	@ResponseBody
	public ResponseEntity<String> update(@RequestParam Integer promoProjectId,
			@RequestParam String promoProjectTitle,
			@RequestParam String promoProjectContext,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime promoProjectStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime promoProjectEnd,
			@RequestParam String promoType,
			@RequestParam(required = false) String benefitType,
			@RequestParam(required = false) Integer benefitValue,
			@RequestParam(required = false) Integer minOrderAmount,
			@RequestParam(required = false) Integer quota) {
		PromoInput in;
		try {
			in = buildInput(promoProjectTitle, promoProjectContext, promoProjectStart, promoProjectEnd, promoType,
					benefitType, benefitValue, minOrderAmount, quota);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
		if (!promoService.update(promoProjectId, in.title(), in.context(), in.start(), in.end(), in.promoType(),
				in.benefitType(), in.benefitValue(), in.minOrderAmount(), in.quota())) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到這筆活動");
		}
		return ResponseEntity.ok("更新成功");
	}

	// 「新增活動」對話框按「確定」時呼叫：欄位與修改相同，檢查規則共用 buildInput
	@PostMapping("/create")
	@ResponseBody
	public ResponseEntity<String> create(@RequestParam String promoProjectTitle,
			@RequestParam String promoProjectContext,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime promoProjectStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime promoProjectEnd,
			@RequestParam String promoType,
			@RequestParam(required = false) String benefitType,
			@RequestParam(required = false) Integer benefitValue,
			@RequestParam(required = false) Integer minOrderAmount,
			@RequestParam(required = false) Integer quota) {
		PromoInput in;
		try {
			in = buildInput(promoProjectTitle, promoProjectContext, promoProjectStart, promoProjectEnd, promoType,
					benefitType, benefitValue, minOrderAmount, quota);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
		PromoProject created = promoService.create(in.title(), in.context(), in.start(), in.end(), in.promoType(),
				in.benefitType(), in.benefitValue(), in.minOrderAmount(), in.quota());
		return ResponseEntity.ok("新增成功，活動編號：" + created.getPromoProjectId() + "（預設為停用）");
	}

	// 列表的「刪除」按鈕（前端已經跳出確認視窗）：刪除這個活動、它底下的活動商品，
	// 以及（使用者再次確認後）它的購物金異動紀錄
	@PostMapping("/delete")
	@ResponseBody
	public ResponseEntity<String> delete(@RequestParam Integer promoProjectId,
			@RequestParam(defaultValue = "false") boolean withCreditRecords) {
		// 活動有購物金異動紀錄（帳務紀錄）時，不能默默跟著刪：先回 409「creditRecords:筆數」，
		// 前端再讓使用者確認一次，確認後帶 withCreditRecords=true 重送，才會連紀錄一起刪
		long creditRecords = promoService.countCreditTransactions(promoProjectId);
		if (creditRecords > 0 && !withCreditRecords) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body("creditRecords:" + creditRecords);
		}
		try {
			if (!promoService.delete(promoProjectId, withCreditRecords)) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到這筆活動");
			}
		} catch (DataIntegrityViolationException e) {
			// 還有其他資料表用外鍵指到這個活動（目前已知的只有購物金異動紀錄與活動商品，都已處理）
			return ResponseEntity.status(HttpStatus.CONFLICT).body("此活動已被其他資料使用，無法刪除。請改用「狀態」開關停用。");
		}
		return ResponseEntity.ok("刪除成功");
	}

	// 列表上的開關：只切換啟用／停用。要啟用的活動必須已經設定類型與優惠方式，
	// 不然啟用了也不會有任何效果，還會誤以為活動生效
	@PostMapping("/toggle")
	@ResponseBody
	public ResponseEntity<String> toggle(@RequestParam Integer promoProjectId, @RequestParam Integer status) {
		if (status != 0 && status != 1) {
			return ResponseEntity.badRequest().body("狀態不正確");
		}
		PromoProject promo = promoService.findById(promoProjectId);
		if (promo == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到這筆活動");
		}
		if (status == 1 && (!PromoType.isValid(promo.getPromoType()) || !BenefitType.isValid(promo.getBenefitType()))) {
			return ResponseEntity.badRequest().body("請先按「修改」設定活動類型與優惠方式，才能啟用");
		}
		promoService.updateStatus(promoProjectId, status);
		return ResponseEntity.ok(status == 1 ? "已啟用" : "已停用");
	}

	// 新增與修改共用：檢查欄位並依活動類型的規則（PromoType）整理。
	// 優惠方式只有一種選擇就自動帶入；優惠值、最低消費、名額這個類型用不到就存 null（避免從別的類型切換過來時殘留舊值）。
	// 有問題丟 IllegalArgumentException，訊息就是要顯示給使用者的文字
	private PromoInput buildInput(String title, String context, LocalDateTime start, LocalDateTime end,
			String promoType, String benefitType, Integer benefitValue, Integer minOrderAmount, Integer quota) {
		String trimmedTitle = title.trim();
		if (trimmedTitle.isEmpty() || trimmedTitle.length() > 50) {
			throw new IllegalArgumentException("活動標題必填，且不可超過 50 字");
		}
		String trimmedContext = context.trim();
		if (trimmedContext.length() > 255) {
			throw new IllegalArgumentException("活動內容不可超過 255 字");
		}
		if (!end.isAfter(start)) {
			throw new IllegalArgumentException("結束時間必須晚於開始時間");
		}
		PromoType type = PromoType.of(promoType);
		if (type == null) {
			throw new IllegalArgumentException("活動類型不正確");
		}
		String resolvedBenefitType = type.resolveBenefitType(benefitType);
		if (resolvedBenefitType == null) {
			throw new IllegalArgumentException("優惠方式不正確");
		}

		Integer value = null;
		if (type.isUsesBenefitValue()) {
			if (benefitValue == null || benefitValue < 1) {
				throw new IllegalArgumentException("優惠值必須大於 0");
			}
			if (BenefitType.PERCENT_OFF.name().equals(resolvedBenefitType) && benefitValue > 99) {
				throw new IllegalArgumentException("折扣率請填 1～99（90 = 9 折）");
			}
			value = benefitValue;
		}

		Integer min = null;
		if (type.isUsesMinOrderAmount()) {
			if (type.isMinOrderAmountRequired() && (minOrderAmount == null || minOrderAmount < 1)) {
				throw new IllegalArgumentException(type.getLabel() + "必須填最低消費");
			}
			if (minOrderAmount != null && minOrderAmount < 0) {
				throw new IllegalArgumentException("最低消費不可為負數");
			}
			min = minOrderAmount;
		}

		Integer limit = null;
		if (type.isUsesQuota()) {
			if (type.isQuotaRequired() && (quota == null || quota < 1)) {
				throw new IllegalArgumentException(type.getLabel() + "必須填名額");
			}
			if (quota != null && quota < 1) {
				throw new IllegalArgumentException("名額必須大於 0");
			}
			limit = quota;
		}

		return new PromoInput(trimmedTitle, trimmedContext, start, end, type.name(), resolvedBenefitType, value, min,
				limit);
	}
}
