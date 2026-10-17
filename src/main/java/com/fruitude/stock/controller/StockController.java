package com.fruitude.stock.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.product.model.ProductSku;
import com.fruitude.stock.model.StockService;
import com.fruitude.utils.PsiUtils;

@Controller
@RequestMapping("/admin/psi/stock")
public class StockController {

	// 商品名稱、規格名稱查詢的字數上限，和供應商名稱（VendorVO 的 vendorName）相同
	private static final int NAME_MAX_LENGTH = 30;

	@Autowired
	StockService stockSvc;

	// 庫存管理系統首頁：列出規格的庫存數字，每頁 20 筆；page 是頁碼，從 1 開始，網址沒帶就是第 1 頁
	// 上方的查詢表單可以同時填 skuId、productName、skuName、status 多個條件，條件之間是「而且」；沒填的條件不使用，都沒填就是列出全部規格
	// 輸入不正確：所有錯誤一行一個顯示在紅框，列表改列出全部規格的第 1 頁
	// 條件正確但查無資料：紅框顯示「查無資料」，表格顯示查無符合條件的規格
	@GetMapping("")
	public String stock(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "skuId", required = false) String skuId,
			@RequestParam(value = "productName", required = false) String productName,
			@RequestParam(value = "skuName", required = false) String skuName, Model model) {

		String skuIdText = skuId == null ? "" : skuId.trim();
		String statusText = status == null ? "" : status.trim();
		String productNameText = productName == null ? "" : productName.trim();
		String skuNameText = skuName == null ? "" : skuName.trim();
		boolean searching = !skuIdText.isEmpty() || !statusText.isEmpty() || !productNameText.isEmpty()
				|| !skuNameText.isEmpty();

		Page<ProductSku> skuPage;
		// 頁碼連結要接在 page 後面的查詢條件（例如 &status=1&productName=...），換頁時才會留在同一個查詢結果
		String queryString = "";

		List<String> errors = validateStockSearch(skuIdText, statusText, productNameText, skuNameText);
		if (!errors.isEmpty()) {
			// 條件不正確：列出全部規格的第 1 頁，翻頁連結不帶條件；表單由 param 回填使用者原本輸入的內容
			model.addAttribute("errorMessage", String.join("\n", errors));
			skuPage = stockSvc.getSkus(1);
			searching = false;
		} else if (!searching) {
			skuPage = stockSvc.getSkus(page);
		} else {
			Integer skuIdValue = null;
			boolean skuIdOverflow = false;
			if (!skuIdText.isEmpty()) {
				try {
					skuIdValue = Integer.valueOf(skuIdText);
				} catch (NumberFormatException e) {
					// 數字太大，超過規格編號的範圍，一定查不到
					skuIdOverflow = true;
				}
			}
			skuPage = skuIdOverflow ? Page.empty()
					: stockSvc.searchSkus(skuIdValue, statusText.isEmpty() ? null : Byte.valueOf(statusText),
							productNameText, skuNameText, page);
			if (skuPage.getTotalElements() == 0) {
				model.addAttribute("errorMessage", "查無資料");
			}

			if (!skuIdText.isEmpty()) {
				queryString += "&skuId=" + skuIdText;
			}
			if (!statusText.isEmpty()) {
				queryString += "&status=" + statusText;
			}
			if (!productNameText.isEmpty()) {
				queryString += "&productName=" + URLEncoder.encode(productNameText, StandardCharsets.UTF_8);
			}
			if (!skuNameText.isEmpty()) {
				queryString += "&skuName=" + URLEncoder.encode(skuNameText, StandardCharsets.UTF_8);
			}
		}

		model.addAttribute("skuListData", skuPage);
		model.addAttribute("queryString", queryString);
		// 有沒有用查詢條件查：查無資料時決定表格顯示哪一句提示
		model.addAttribute("searching", searching);
		return "admin/psi/stock/index"; //view
	}

	// 庫存查詢條件的格式檢查，回傳所有錯誤訊息（沒有錯誤時是空的清單）；傳進來的值都已經去掉前後空白，沒填是空字串
	// 規格編號溢位（數字太大）不算格式錯誤，由 stock 當成查無資料
	private List<String> validateStockSearch(String skuId, String status, String productName, String skuName) {
		List<String> errors = new ArrayList<>();
		// 不檢查上限：數字太大時由 stock 當成查無資料
		String skuIdError = PsiUtils.positiveIdError(skuId, "規格編號", null);
		if (skuIdError != null) {
			errors.add(skuIdError);
		}
		if (productName.length() > NAME_MAX_LENGTH) {
			errors.add("商品名稱，字數過多");
		}
		if (skuName.length() > NAME_MAX_LENGTH) {
			errors.add("規格名稱，字數過多");
		}
		// 狀態碼 0 到 7，對應 ProductSku 的 getStatusLabel；下拉選單的「全部」是空字串，不檢查
		if (!status.isEmpty() && !status.matches("^[0-7]$")) {
			errors.add("規格狀態，值不正確");
		}
		return errors;
	}

	// 單筆資料：由首頁列表的「檢視」連過來，依 skuId 顯示這個規格的資料與庫存數量
	@GetMapping("/listOneStock")
	public String listOneStock(@RequestParam("skuId") Integer skuId, Model model,
			RedirectAttributes redirectAttributes) {
		ProductSku productSku = stockSvc.getOneSku(skuId);

		// 查不到規格時回首頁顯示錯誤訊息
		if (productSku == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "查無此規格");
			return "redirect:/admin/psi/stock";
		}
		model.addAttribute("productSku", productSku);

		return "admin/psi/stock/listOneStock"; //view
	}

	// 調整頁：由首頁列表或單筆頁的「更新」連過來，顯示這個規格可以調整的四個數字
	@GetMapping("/updateStock")
	public String updateStock(@RequestParam("skuId") Integer skuId, Model model,
			RedirectAttributes redirectAttributes) {
		ProductSku productSku = stockSvc.getOneSku(skuId);
		String stockContent = stockSvc.getStockContent(skuId);

		// 查不到規格時回首頁顯示錯誤訊息
		if (productSku == null || stockContent == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "查無此規格");
			return "redirect:/admin/psi/stock";
		}

		// 輸入框的數字直接從內容字串拆出來（庫存;安全庫存;允許超賣量;採購溢額量），畫面上看到的和存檔時拿來比對的才會是同一份
		String[] quantities = stockContent.split(";");
		model.addAttribute("productSku", productSku);
		model.addAttribute("stockContent", stockContent);
		model.addAttribute("stock", quantities[0]);
		model.addAttribute("safetyStock", quantities[1]);
		model.addAttribute("maxBackorderQty", quantities[2]);
		model.addAttribute("purchaseAddOnQty", quantities[3]);

		return "admin/psi/stock/updateStock"; //view
	}

	// 調整存檔：由調整頁的「儲存」送過來，更新庫存、安全庫存、允許超賣量、採購溢額量
	// 四個數字都用字串接收再自己檢查，空白、不是 0 以上的整數、數字過大各有自己的錯誤訊息；有錯誤就回到調整頁
	// stockContent 是調整頁顯示當時的內容字串；沒有帶或和資料庫當下的內容不一樣時不存檔，回單筆頁顯示原因
	@PostMapping("/updateStock")
	public String updateStock(@RequestParam("skuId") Integer skuId,
			@RequestParam(value = "stock", required = false) String stock,
			@RequestParam(value = "safetyStock", required = false) String safetyStock,
			@RequestParam(value = "maxBackorderQty", required = false) String maxBackorderQty,
			@RequestParam(value = "purchaseAddOnQty", required = false) String purchaseAddOnQty,
			@RequestParam(value = "stockContent", required = false) String stockContent, Model model,
			RedirectAttributes redirectAttributes) {

		// key 是欄位名稱，調整頁用來把錯誤訊息顯示在對應的輸入框下方
		Map<String, String> fieldErrors = new HashMap<>();
		Integer stockValue = parseQuantity("stock", "庫存數量", stock, fieldErrors);
		Integer safetyStockValue = parseQuantity("safetyStock", "安全庫存", safetyStock, fieldErrors);
		Integer maxBackorderQtyValue = parseQuantity("maxBackorderQty", "允許超賣量", maxBackorderQty, fieldErrors);
		Integer purchaseAddOnQtyValue = parseQuantity("purchaseAddOnQty", "採購溢額量", purchaseAddOnQty, fieldErrors);

		if (!fieldErrors.isEmpty()) {
			ProductSku productSku = stockSvc.getOneSku(skuId);
			if (productSku == null) {
				redirectAttributes.addFlashAttribute("errorMessage", "查無此規格");
				return "redirect:/admin/psi/stock";
			}
			// 重新顯示調整頁：輸入框保留使用者輸入的內容，表單繼續帶著一開始的內容字串，不能換成現在的
			model.addAttribute("productSku", productSku);
			model.addAttribute("stockContent", stockContent);
			model.addAttribute("stock", stock);
			model.addAttribute("safetyStock", safetyStock);
			model.addAttribute("maxBackorderQty", maxBackorderQty);
			model.addAttribute("purchaseAddOnQty", purchaseAddOnQty);
			model.addAttribute("fieldErrors", fieldErrors);
			return "admin/psi/stock/updateStock";
		}

		boolean modified;
		try {
			modified = stockSvc.updateStockQuantities(skuId, stockValue, safetyStockValue, maxBackorderQtyValue,
					purchaseAddOnQtyValue, stockContent);
		} catch (IllegalArgumentException e) {
			// 查無此規格，或調整頁開著的期間資料被別人改過：回單筆頁顯示原因與最新的數字（查無此規格時單筆頁會再導回首頁）
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/psi/stock/listOneStock?skuId=" + skuId;
		}

		redirectAttributes.addFlashAttribute("success", modified ? "庫存資料已更新" : "資料沒有變更");
		return "redirect:/admin/psi/stock/listOneStock?skuId=" + skuId;
	}

	// 調整頁一個數字欄位的檢查：通過時回傳數字；不通過時回傳 null，並把錯誤訊息放進 fieldErrors
	// fieldName 是欄位名稱（fieldErrors 的 key），label 是錯誤訊息開頭的中文名稱，text 是使用者輸入的內容
	private Integer parseQuantity(String fieldName, String label, String text, Map<String, String> fieldErrors) {
		String quantityText = text == null ? "" : text.trim();
		if (quantityText.isEmpty()) {
			fieldErrors.put(fieldName, label + "，請勿空白");
			return null;
		}
		// 英文字、符號、負號、小數點都不是數字
		if (!quantityText.matches("^\\d+$")) {
			fieldErrors.put(fieldName, label + "，只能輸入 0 以上的整數");
			return null;
		}
		try {
			return Integer.valueOf(quantityText);
		} catch (NumberFormatException e) {
			fieldErrors.put(fieldName, label + "，數字過大");
			return null;
		}
	}

	// 網址的參數型別不對（例如 page=abc）或少了必要的參數時，回首頁第 1 頁並顯示錯誤訊息
	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
	public ModelAndView handleError(Exception e) {
		ModelAndView mav = new ModelAndView("admin/psi/stock/index");
		mav.addObject("errorMessage", "網址的參數不正確，請從庫存管理系統重新操作");
		mav.addObject("skuListData", stockSvc.getSkus(1));
		mav.addObject("queryString", "");
		return mav;
	}

}
