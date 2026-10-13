package com.fruitude.stock.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

@Controller
@RequestMapping("/admin/psi/stock")
public class StockController {

	// 商品名稱、規格名稱查詢的字數上限，和供應商名稱（VendorVO 的 vendorName）相同
	private static final int NAME_MAX_LENGTH = 30;

	@Autowired
	StockService stockSvc;

	// 庫存管理系統首頁：列出規格的庫存數字，每頁 20 筆；page 是頁碼，從 1 開始，網址沒帶就是第 1 頁
	// 上方四個查詢表單各自獨立，一次只會帶 skuId、productName、skuName、status 其中一個；都沒帶就是列出全部規格
	// 輸入不正確或查無資料時，顯示錯誤訊息並列出全部規格的第 1 頁
	@GetMapping("")
	public String stock(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "skuId", required = false) String skuId,
			@RequestParam(value = "productName", required = false) String productName,
			@RequestParam(value = "skuName", required = false) String skuName, Model model) {

		String errorMessage = null;
		Page<ProductSku> skuPage = null;
		// 頁碼連結要接在 page 後面的查詢條件（例如 &status=1），換頁時才會留在同一個查詢結果
		String queryString = "";
		boolean searching = true;

		if (skuId != null) {
			String skuIdText = skuId.trim();
			if (skuIdText.isEmpty()) {
				errorMessage = "規格編號，請勿空白";
			} else if (!skuIdText.matches("^\\d+$") || skuIdText.matches("^0+$")) {
				// 英文字、符號、負號、小數點都不是數字；0 不是正整數
				errorMessage = "規格編號，只能輸入正整數";
			} else {
				try {
					skuPage = stockSvc.getSkusBySkuId(Integer.valueOf(skuIdText));
					queryString = "&skuId=" + skuIdText;
				} catch (NumberFormatException e) {
					// 數字太大，超過規格編號的範圍，一定查不到；skuPage 留著 null，下面會當成查無資料
				}
			}
		} else if (productName != null) {
			String keyword = productName.trim();
			if (keyword.isEmpty()) {
				errorMessage = "商品名稱，請勿空白";
			} else if (keyword.length() > NAME_MAX_LENGTH) {
				errorMessage = "商品名稱，字數過多";
			} else {
				skuPage = stockSvc.getSkusByProductName(keyword, page);
				queryString = "&productName=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
			}
		} else if (skuName != null) {
			String keyword = skuName.trim();
			if (keyword.isEmpty()) {
				errorMessage = "規格名稱，請勿空白";
			} else if (keyword.length() > NAME_MAX_LENGTH) {
				errorMessage = "規格名稱，字數過多";
			} else {
				skuPage = stockSvc.getSkusBySkuName(keyword, page);
				queryString = "&skuName=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
			}
		} else if (status != null && !status.isEmpty()) {
			// 狀態碼 0 到 7，對應 ProductSku 的 getStatusLabel；下拉選單的「查詢全部」是空字串，不會進到這裡
			if (!status.matches("^[0-7]$")) {
				errorMessage = "規格狀態，值不正確";
			} else {
				skuPage = stockSvc.getSkusByStatus(Byte.valueOf(status), page);
				queryString = "&status=" + status;
				// 下拉選單用來標示目前選的狀態
				model.addAttribute("status", status);
			}
		} else {
			searching = false;
			skuPage = stockSvc.getSkus(page);
		}

		if (searching && errorMessage == null && (skuPage == null || skuPage.getTotalElements() == 0)) {
			errorMessage = "查無資料";
		}
		if (errorMessage != null) {
			model.addAttribute("errorMessage", errorMessage);
			model.addAttribute("status", null);
			skuPage = stockSvc.getSkus(1);
			queryString = "";
		}

		model.addAttribute("skuListData", skuPage);
		model.addAttribute("queryString", queryString);
		return "admin/psi/stock/index"; //view
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
