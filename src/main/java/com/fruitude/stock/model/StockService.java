package com.fruitude.stock.model;

import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.product.model.ProductSku;

@Service
public class StockService {

	// 首頁規格列表每頁顯示的筆數
	private static final int PAGE_SIZE = 20;

	// 規格編號由小到大
	private static final Sort SKUS_SORT = Sort.by("skuId");

	@Autowired
	private StockSkuRepository skuRepository;

	// 全部規格（包含下架、售完、永久停產）的其中一頁，規格編號由小到大
	// page 是畫面上的頁碼，從 1 開始；小於 1 當作第 1 頁，超過最後一頁就回傳最後一頁
	public Page<ProductSku> getSkus(int page) {
		return getSkuPage(page, pageable -> skuRepository.findSkusWithProduct(pageable));
	}

	// 複合條件查詢的其中一頁，條件之間是「而且」；page 的規則和 getSkus 相同
	// 條件都可以不填（null 或空字串）：skuId、status 完全相同，productName、skuName 找部分文字
	public Page<ProductSku> searchSkus(Integer skuId, Byte status, String productName, String skuName, int page) {
		// 沒填的條件換成「不篩選」的值，對應 StockSkuRepository 的 searchSkus
		int skuIdFilter = skuId == null ? 0 : skuId;
		int statusFilter = status == null ? 0 : 1;
		Byte statusValue = status == null ? Byte.valueOf((byte) 0) : status; // 不篩選時不會用到，給個值避免傳 null
		String productNamePattern = productName == null || productName.isEmpty() ? "" : toLikePattern(productName);
		String skuNamePattern = skuName == null || skuName.isEmpty() ? "" : toLikePattern(skuName);
		return getSkuPage(page, pageable -> skuRepository.searchSkus(skuIdFilter, statusFilter, statusValue,
				productNamePattern, skuNamePattern, pageable));
	}

	// 單筆規格，查不到回傳 null
	public ProductSku getOneSku(Integer skuId) {
		return skuRepository.findById(skuId).orElse(null);
	}

	// 調整頁可以改的四個數字目前的內容字串（庫存;安全庫存;允許超賣量;採購溢額量），查無此規格回傳 null
	// 調整頁顯示時算一次放在表單裡，存檔時 updateStockQuantities 再以資料庫當下的內容算一次，兩個不一樣就代表調整頁開著的期間被別人改過
	public String getStockContent(Integer skuId) {
		return skuRepository.findStockContent(skuId);
	}

	// 調整頁存檔：更新庫存、安全庫存、允許超賣量、採購溢額量，回傳有沒有寫入（四個數字都沒變時不寫入，回傳 false）
	// stockContent 是調整頁顯示當時的內容字串，沒有帶或和資料庫當下的內容不一樣時不存檔
	// 全部在同一個交易裡；查無此規格或內容被改過時丟 IllegalArgumentException，資料不會被改到
	@Transactional
	public boolean updateStockQuantities(Integer skuId, Integer stock, Integer safetyStock, Integer maxBackorderQty,
			Integer purchaseAddOnQty, String stockContent) {
		// 先鎖住規格再比對：出貨、驗收剛好同時改庫存時，要等這個交易結束，不會在比對之後、寫入之前插進來
		String dbStockContent = skuRepository.lockStockContent(skuId);
		if (dbStockContent == null) {
			throw new IllegalArgumentException("查無此規格");
		}
		if (!dbStockContent.equals(stockContent)) {
			throw new IllegalArgumentException("這個規格的庫存資料已經被更動，請確認最新的數字後再調整");
		}

		// 新的四個數字和資料庫當下的一樣就不寫入，不然只會白白更新修改時間
		String newStockContent = stock + ";" + safetyStock + ";" + maxBackorderQty + ";" + purchaseAddOnQty;
		if (newStockContent.equals(dbStockContent)) {
			return false;
		}

		// 前面已經鎖住並確認規格存在，正常不會更新到 0 筆
		if (skuRepository.updateQuantities(skuId, stock, safetyStock, maxBackorderQty, purchaseAddOnQty) == 0) {
			throw new IllegalArgumentException("查無此規格");
		}
		return true;
	}

	// 各種查詢共用的頁碼處理：query 是實際查資料庫的那一個 repository 方法
	private Page<ProductSku> getSkuPage(int page, Function<Pageable, Page<ProductSku>> query) {
		int pageIndex = Math.max(page, 1) - 1; // PageRequest 的頁碼從 0 開始
		Page<ProductSku> skuPage = query.apply(PageRequest.of(pageIndex, PAGE_SIZE, SKUS_SORT));
		if (pageIndex > 0 && pageIndex >= skuPage.getTotalPages()) {
			int lastPageIndex = Math.max(skuPage.getTotalPages() - 1, 0);
			skuPage = query.apply(PageRequest.of(lastPageIndex, PAGE_SIZE, SKUS_SORT));
		}
		return skuPage;
	}

	// 把關鍵字轉成模糊查詢用的 LIKE 樣式：前後加 %
	// 關鍵字裡的 %、_ 是 LIKE 的萬用字元，先用 ! 跳脫成一般文字（! 本身也要跳脫），使用者輸入 % 才不會查出全部
	private String toLikePattern(String keyword) {
		String escaped = keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escaped + "%";
	}

}
