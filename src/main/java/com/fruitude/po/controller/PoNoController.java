package com.fruitude.po.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.po.model.PoService;
import com.fruitude.po.model.PoVO;
import com.fruitude.product.model.ProductSku;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 採購單首頁（purchase/index.html）的條件查詢：負責檢查輸入的格式，不通過或查無資料時回首頁顯示訊息
@Controller
@RequestMapping("/admin/psi/purchase")
public class PoNoController {

	@Autowired
	PoService poSvc;

	// 這支 controller 的每個請求都會先執行，把低於安全庫存的規格放進 model，回首頁時顯示待採購商品
	// key 是供應商編號、value 是該供應商的規格清單；例外處理（handleError）不會經過這裡，要自己再放一次
	@ModelAttribute("belowSafetyStockByVendor")
	public Map<Integer, List<ProductSku>> belowSafetyStockByVendor() {
		return poSvc.getBelowSafetyStockByVendor();
	}

	// 待採購商品的「待審核數量」欄：key 是規格編號、value 是待審核採購單裡的採購數量加總；handleError 一樣要自己再放一次
	@ModelAttribute("pendingQuantityBySkuId")
	public Map<Integer, Long> pendingQuantityBySkuId() {
		return poSvc.getPendingQuantityBySkuId();
	}

	// 待採購商品的各供應商區塊要更新的待審核採購單：key 是供應商編號、value 是採購單系統編號；有值的區塊顯示「更新採購單」而不是「新增採購單」
	@ModelAttribute("pendingPoIdByVendorId")
	public Map<Integer, Integer> pendingPoIdByVendorId() {
		return poSvc.getPendingPoIdByVendorId();
	}

	// 依採購單編號查單筆，查到後交給 PoController 的 listOnePo 顯示
	@PostMapping("/getOneForDisplay")
	public String getOneForDisplay(Model model, @RequestParam(value = "poNo", required = false) String poNo,
			RedirectAttributes redirectAttributes) {

		String errorMessage = null;
		PoVO poVO = null;

		if (poNo == null || poNo.isBlank()) {
			errorMessage = "採購單編號，請勿空白";
		} else if (!poNo.trim().toUpperCase().matches("^PO\\d{12}$")) {
			// PoService 的 generatePoNo 產生的格式：PO + 日期 8 碼 + 流水號 4 碼
			errorMessage = "採購單編號: 格式為 PO 加上 12 位數字，例如 PO202610090001";
		} else {
			poVO = poSvc.getOneByPoNo(poNo.trim().toUpperCase());
			if (poVO == null) {
				errorMessage = "查無資料";
			}
		}

		if (errorMessage != null) {
			model.addAttribute("errorMessage", errorMessage);
			return "admin/psi/purchase/index";
		}

		redirectAttributes.addAttribute("poId", poVO.getPoId());
		return "redirect:/admin/psi/purchase/listOnePo";
	}

	// 依供應商編號查該供應商的採購單，結果以 listAllPo.html 顯示
	@PostMapping("/listPosByVendorId")
	public String listPosByVendorId(
			@RequestParam(value = "vendorId", required = false) @NotNull(message = "供應商編號，請勿空白") @Min(value = 1, message = "供應商編號: 不能小於{value}") @Max(value = 99999, message = "供應商編號: 不能大於{value}") Integer vendorId,
			Model model) {

		return showSearchResult(model, poSvc.getByVendorId(vendorId), "供應商編號 " + vendorId + " 的採購單");
	}

	// 依採購員工編號查該員工建立的採購單，結果以 listAllPo.html 顯示
	@PostMapping("/listPosByPoEmployeeId")
	public String listPosByPoEmployeeId(
			@RequestParam(value = "poEmployeeId", required = false) @NotNull(message = "採購員工編號，請勿空白") @Min(value = 1, message = "採購員工編號: 不能小於{value}") @Max(value = 99999, message = "採購員工編號: 不能大於{value}") Integer poEmployeeId,
			Model model) {

		return showSearchResult(model, poSvc.getByPoEmployeeId(poEmployeeId),
				"採購員工編號 " + poEmployeeId + " 的採購單");
	}

	// 列表搜尋共用：查無資料時回首頁，否則以 listAllPo.html 顯示
	private String showSearchResult(Model model, List<PoVO> pos, String searchTitle) {
		if (pos.isEmpty()) {
			model.addAttribute("errorMessage", "查無資料");
			return "admin/psi/purchase/index";
		}

		model.addAttribute("searchTitle", searchTitle);
		model.addAttribute("poListData", pos);
		return "admin/psi/purchase/listAllPo";
	}

	// 只處理這支 controller 的方法丟出的例外，回首頁顯示訊息
	@ExceptionHandler({ HandlerMethodValidationException.class, ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class })
	public ModelAndView handleError(Exception e) {

		List<String> messages = new ArrayList<>();

		if (e instanceof HandlerMethodValidationException ex) {
			// 方法參數驗證失敗（@NotNull、@Min、@Max）
			ex.getParameterValidationResults().forEach(
					result -> result.getResolvableErrors().forEach(error -> messages.add(error.getDefaultMessage())));
		} else if (e instanceof ConstraintViolationException ex) {
			// 類別上有加 @Validated 時會丟這個
			ex.getConstraintViolations().forEach(violation -> messages.add(violation.getMessage()));
		} else if (e instanceof MethodArgumentTypeMismatchException ex) {
			// 轉型失敗（輸入英文字、超過 int 範圍）；依出錯的參數名稱顯示對應的欄位
			String fieldLabel = "poEmployeeId".equals(ex.getName()) ? "採購員工編號" : "供應商編號";
			messages.add(fieldLabel + "，只能輸入正整數");
		}

		ModelAndView mav = new ModelAndView("admin/psi/purchase/index");
		mav.addObject("errorMessage", String.join("\n", messages));
		mav.addObject("belowSafetyStockByVendor", poSvc.getBelowSafetyStockByVendor());
		mav.addObject("pendingQuantityBySkuId", poSvc.getPendingQuantityBySkuId());
		mav.addObject("pendingPoIdByVendorId", poSvc.getPendingPoIdByVendorId());
		return mav;
	}

}
