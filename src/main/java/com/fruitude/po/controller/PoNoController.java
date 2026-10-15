package com.fruitude.po.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fruitude.po.model.PoService;
import com.fruitude.po.model.PoVO;
import com.fruitude.utils.PsiUtils;

// 採購單首頁（purchase/index.html）的複合條件查詢：負責檢查輸入的格式，不通過或查無資料時回首頁顯示訊息
@Controller
@RequestMapping("/admin/psi/purchase")
public class PoNoController {

	// 供應商編號、採購員工編號的上限
	private static final int ID_MAX = 99999;

	// 採購單狀態碼對應的文字，和 index.html、listAllPo.html 的狀態文字相同；查詢結果的標題用
	private static final Map<Integer, String> PO_STATUS_LABELS = Map.of(0, "待審核", 1, "申請通過", 2, "申請未通過",
			3, "已取消", 4, "已結案");

	@Autowired
	PoService poSvc;

	// 複合條件查詢：採購單編號、採購單狀態、供應商編號、採購員工編號可以同時填，條件之間是「而且」；沒填的條件不使用，都沒填就是全部採購單
	// 輸入不正確：所有錯誤一行一個，回首頁顯示在紅框；查無資料：回首頁顯示「查無資料」；兩種情況表單都保留使用者輸入的內容（index.html 用 param 回填）
	// 查到資料：以 listAllPo.html 顯示，標題列出這次用的條件（searchTitle）
	@PostMapping("/searchPos")
	public String searchPos(Model model, @RequestParam(value = "poNo", required = false) String poNo,
			@RequestParam(value = "poStatus", required = false) String poStatus,
			@RequestParam(value = "vendorId", required = false) String vendorId,
			@RequestParam(value = "poEmployeeId", required = false) String poEmployeeId) {

		// 採購單編號不分大小寫，一律轉成大寫再檢查與查詢
		String poNoText = poNo == null ? "" : poNo.trim().toUpperCase();
		String poStatusText = poStatus == null ? "" : poStatus.trim();
		String vendorIdText = vendorId == null ? "" : vendorId.trim();
		String poEmployeeIdText = poEmployeeId == null ? "" : poEmployeeId.trim();

		List<String> errors = validatePoSearch(poNoText, poStatusText, vendorIdText, poEmployeeIdText);
		if (!errors.isEmpty()) {
			model.addAttribute("errorMessage", String.join("\n", errors));
			model.addAllAttributes(poSvc.getIndexData());
			return "admin/psi/purchase/index";
		}

		List<PoVO> pos = poSvc.searchPos(poNoText.isEmpty() ? null : poNoText,
				poStatusText.isEmpty() ? null : Byte.valueOf(poStatusText),
				vendorIdText.isEmpty() ? null : Integer.valueOf(vendorIdText),
				poEmployeeIdText.isEmpty() ? null : Integer.valueOf(poEmployeeIdText));
		if (pos.isEmpty()) {
			model.addAttribute("errorMessage", "查無資料");
			model.addAllAttributes(poSvc.getIndexData());
			return "admin/psi/purchase/index";
		}

		// 標題：列出有填的條件，例如「採購單狀態：待審核、供應商編號：3」；都沒填時不放，listAllPo.html 顯示「顯示全部採購單」
		List<String> conditions = new ArrayList<>();
		if (!poNoText.isEmpty()) {
			conditions.add("採購單編號：" + poNoText);
		}
		if (!poStatusText.isEmpty()) {
			conditions.add("採購單狀態：" + PO_STATUS_LABELS.get(Integer.parseInt(poStatusText)));
		}
		if (!vendorIdText.isEmpty()) {
			conditions.add("供應商編號：" + Integer.valueOf(vendorIdText));
		}
		if (!poEmployeeIdText.isEmpty()) {
			conditions.add("採購員工編號：" + Integer.valueOf(poEmployeeIdText));
		}
		if (!conditions.isEmpty()) {
			model.addAttribute("searchTitle", String.join("、", conditions));
		}
		model.addAttribute("poListData", pos);
		return "admin/psi/purchase/listAllPo";
	}

	// 複合條件查詢的格式檢查，回傳所有錯誤訊息（沒有錯誤時是空的清單）；傳進來的值都已經去掉前後空白，沒填是空字串
	private List<String> validatePoSearch(String poNo, String poStatus, String vendorId, String poEmployeeId) {
		List<String> errors = new ArrayList<>();
		// PoService 的 generatePoNo 產生的格式：PO + 日期 8 碼 + 流水號 4 碼
		if (!poNo.isEmpty() && !poNo.matches("^PO\\d{12}$")) {
			errors.add("採購單編號: 格式為 PO 加上 12 位數字，例如 PO202610090001");
		}
		// 狀態碼 0 到 4，對應 PO_STATUS_LABELS；下拉選單的「查詢全部」是空字串，不檢查
		if (!poStatus.isEmpty() && !poStatus.matches("^[0-4]$")) {
			errors.add("採購單狀態，值不正確");
		}
		// 供應商編號、採購員工編號：有填才檢查，要是 1 到 ID_MAX 的正整數
		String vendorIdError = PsiUtils.positiveIdError(vendorId, "供應商編號", ID_MAX);
		if (vendorIdError != null) {
			errors.add(vendorIdError);
		}
		String poEmployeeIdError = PsiUtils.positiveIdError(poEmployeeId, "採購員工編號", ID_MAX);
		if (poEmployeeIdError != null) {
			errors.add(poEmployeeIdError);
		}
		return errors;
	}

}
