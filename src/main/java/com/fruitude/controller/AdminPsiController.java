package com.fruitude.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 進銷存五個分頁首頁的另一組網址（資料夾結尾斜線、/index、/index.html）集中在這裡導回正常的首頁
// FruitudeWebConfig 會替每個 index.html 登記這三個網址並直接顯示樣板，不經過 controller、沒有資料；
// 這裡先接住，導回各分頁自己的 controller，資料仍由它們準備
@Controller
@RequestMapping("/admin/psi")
public class AdminPsiController {

	// 出貨系統首頁由 ShippingController 的 shipping 處理
	@GetMapping({ "/shipping/", "/shipping/index", "/shipping/index.html" })
	public String oldShipping() {
		return "redirect:/admin/psi/shipping";
	}

	// 進貨系統首頁由 ReceivingController 的 receiving 處理
	@GetMapping({ "/receiving/", "/receiving/index", "/receiving/index.html" })
	public String oldReceiving() {
		return "redirect:/admin/psi/receiving";
	}

	// 採購系統首頁由 PoController 的 purchase 處理
	@GetMapping({ "/purchase/", "/purchase/index", "/purchase/index.html" })
	public String oldPurchase() {
		return "redirect:/admin/psi/purchase";
	}

	// 庫存管理系統首頁由 StockController 的 stock 處理
	@GetMapping({ "/stock/", "/stock/index", "/stock/index.html" })
	public String oldStock() {
		return "redirect:/admin/psi/stock";
	}

	// 供應商管理系統首頁由 VendorController 的 vendor 處理
	@GetMapping({ "/vendor/", "/vendor/index", "/vendor/index.html" })
	public String oldVendor() {
		return "redirect:/admin/psi/vendor";
	}

//	 進銷存各分頁底下以 .html 結尾的網址（例如 /admin/psi/vendor/listOneVendor.html）導回該分頁的首頁
//	 FruitudeWebConfig 把 templates/admin 整個資料夾當成靜態檔案提供，沒有 controller 接的 .html 網址會直接送出樣板原始檔；
//	 這裡先接住，就輪不到那個機制。分頁名稱不是進銷存的五個分頁時回 404
	@GetMapping("/{tab}/{page}.html")
	public String oldPsiHtmlPage(@org.springframework.web.bind.annotation.PathVariable("tab") String tab) {
		if (!java.util.Set.of("shipping", "receiving", "purchase", "stock", "vendor").contains(tab)) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND);
		}
		return "redirect:/admin/psi/" + tab;
	}

}
