package com.fruitude.promo.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.fruitude.promo.model.PromoProject;
import com.fruitude.promo.model.PromoService;

/**
 * 後台「活動管理」頁面（目前只是頁面的殼：版型、工具列、表頭都在，還沒有讀取資料）。
 * 對應資料表 promo_project（活動專案）。
 */
@Controller
@RequestMapping("/admin/promo")
public class AdminPromoController {
	@Autowired
	private PromoService promoService;

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
		model.addAttribute("promotions", promoService.findPromotionsByProjectId(promoProjectId));
		return "admin/promo/detail/index";
	}

	// 編輯對話框按「確定」時呼叫：更新這筆活動的標題、內容、開始與結束時間。
	// 欄位檢查在伺服器端做，不能信任瀏覽器送來的值
	@PostMapping("/update")
	@ResponseBody
	public ResponseEntity<String> update(@RequestParam Integer promoProjectId,
			@RequestParam String promoProjectTitle,
			@RequestParam String promoProjectContext,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime promoProjectStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime promoProjectEnd) {
		String title = promoProjectTitle.trim();
		String context = promoProjectContext.trim();
		if (title.isEmpty() || title.length() > 50) {
			return ResponseEntity.badRequest().body("活動標題必填，且不可超過 50 字");
		}
		if (context.length() > 255) {
			return ResponseEntity.badRequest().body("活動內容不可超過 255 字");
		}
		if (!promoProjectEnd.isAfter(promoProjectStart)) {
			return ResponseEntity.badRequest().body("結束時間必須晚於開始時間");
		}
		if (!promoService.update(promoProjectId, title, context, promoProjectStart, promoProjectEnd)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到這筆活動");
		}
		return ResponseEntity.ok("更新成功");
	}
}
