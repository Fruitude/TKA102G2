package com.fruitude.chat.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fruitude.product.model.FrontCatalogService;

// 客服輸入框的商品建議：客服打「#關鍵字」時，列出前台上架中的商品與規格讓客服點選帶入商品名稱
// 商品本身沒有價格（價格在規格上），這裡也不回傳價格；比對與排序由 static/js/chat/chat-product-suggest.js 在前端做
@Service
public class ChatProductService {

	// 規格可以購買的狀態，和 ChatQuizService.buyable 相同
	private static final Set<Integer> BUYABLE_STATUSES = Set.of(1, 2, 3, 6);

	@Autowired
	private FrontCatalogService catalog;

	// soldOut：沒有庫存或狀態不能購買
	public record SkuOption(Integer skuId, String name, boolean soldOut) {
	}

	public record ProductOption(Integer productId, String name, List<SkuOption> skus) {
	}

	// 前台看得到的上架商品與它的規格
	public List<ProductOption> getProductOptions() {
		List<ProductOption> options = new ArrayList<>();
		for (FrontCatalogService.ProductView product : catalog.getProducts()) {
			List<SkuOption> skus = new ArrayList<>();
			for (FrontCatalogService.SkuView sku : product.skus()) {
				skus.add(new SkuOption(sku.skuId(), sku.name(), !buyable(sku)));
			}
			options.add(new ProductOption(product.productId(), product.name(), skus));
		}
		return options;
	}

	private static boolean buyable(FrontCatalogService.SkuView sku) {
		return sku.stock() != null && sku.stock() > 0
				&& sku.skuStatus() != null && BUYABLE_STATUSES.contains(sku.skuStatus());
	}

}
