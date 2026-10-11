package com.fruitude.shipping.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.orders.model.Orders;
import com.fruitude.orders.model.OrdersDetail;
import com.fruitude.product.model.ProductSku;

@Service
public class ShippingService {

	// 首頁待出貨訂單每頁顯示的筆數
	private static final int PAGE_SIZE = 10;

	// 訂單日期由舊到新，同一時間的再依訂單編號由小到大
	private static final Sort PENDING_ORDERS_SORT = Sort.by("ordersDate", "ordersId");

	@Autowired
	private ShippingOrderRepository ordersRepository;

	@Autowired
	private ShippingOrdersDetailRepository ordersDetailRepository;

	@Autowired
	private ShippingSkuStockRepository skuStockRepository;

	// 待出貨的訂單（orders_status = 0）的其中一頁，訂單日期由舊到新
	// page 是畫面上的頁碼，從 1 開始；小於 1 當作第 1 頁，超過最後一頁就回傳最後一頁
	public Page<Orders> getPendingOrders(int page) {
		int pageIndex = Math.max(page, 1) - 1; // PageRequest 的頁碼從 0 開始
		Page<Orders> ordersPage = ordersRepository.findByOrdersStatus(0, PageRequest.of(pageIndex, PAGE_SIZE, PENDING_ORDERS_SORT));
		if (pageIndex > 0 && pageIndex >= ordersPage.getTotalPages()) {
			int lastPageIndex = Math.max(ordersPage.getTotalPages() - 1, 0);
			ordersPage = ordersRepository.findByOrdersStatus(0, PageRequest.of(lastPageIndex, PAGE_SIZE, PENDING_ORDERS_SORT));
		}
		return ordersPage;
	}

	// 單筆訂單，查不到回傳 null
	public Orders getOneOrders(Integer ordersId) {
		return ordersRepository.findById(ordersId).orElse(null);
	}

	// 一張訂單的出貨明細：每筆訂單明細配上它的商品規格，依明細編號由小到大
	public List<ShippingDetail> getShippingDetails(Integer ordersId) {
		List<OrdersDetail> ordersDetails = ordersDetailRepository.findByOrdersIdOrderByOrdersDetailIdAsc(ordersId);

		// 這張訂單用到的規格一次查出來，不要每筆明細各查一次；key 是規格編號
		List<Integer> skuIds = new ArrayList<>();
		for (OrdersDetail ordersDetail : ordersDetails) {
			if (ordersDetail.getSkuId() != null) {
				skuIds.add(ordersDetail.getSkuId());
			}
		}
		Map<Integer, ProductSku> skuBySkuId = new HashMap<>();
		for (ProductSku productSku : skuStockRepository.findAllById(skuIds)) {
			skuBySkuId.put(productSku.getSkuId(), productSku);
		}

		List<ShippingDetail> shippingDetails = new ArrayList<>();
		for (OrdersDetail ordersDetail : ordersDetails) {
			shippingDetails.add(new ShippingDetail(ordersDetail, skuBySkuId.get(ordersDetail.getSkuId())));
		}
		return shippingDetails;
	}

	// 單筆頁的「確認出貨」能不能按：有明細，而且每一筆的庫存都夠
	// 只是畫面上的判斷，真正出貨時 shipOrders 會在鎖住規格之後再檢查一次
	public boolean isShippable(List<ShippingDetail> shippingDetails) {
		if (shippingDetails.isEmpty()) {
			return false;
		}
		for (ShippingDetail shippingDetail : shippingDetails) {
			if (!shippingDetail.isStockEnough()) {
				return false;
			}
		}
		return true;
	}

	// 確認出貨：訂單狀態由待出貨（0）改成已出貨（1），明細的規格扣掉庫存與預計出貨
	// 整張訂單一次出完，全部寫在同一個交易；任何一項檢查沒過就丟 IllegalArgumentException，訂單與庫存都不會被改到
	@Transactional
	public void shipOrders(Integer ordersId) {
		// 先鎖住訂單再看狀態：連按兩次、兩個人同時出同一張單、訂單剛好被取消時，後來的那一個會在這裡被擋下
		Orders orders = ordersRepository.lockOrders(ordersId).orElse(null);
		if (orders == null) {
			throw new IllegalArgumentException("查無此訂單");
		}
		if (!Integer.valueOf(0).equals(orders.getOrdersStatus())) {
			throw new IllegalArgumentException("訂單 " + ordersId + " 已經不是待出貨，無法出貨");
		}

		List<OrdersDetail> ordersDetails = ordersDetailRepository.findByOrdersIdOrderByOrdersDetailIdAsc(ordersId);
		if (ordersDetails.isEmpty()) {
			throw new IllegalArgumentException("這張訂單沒有明細，無法出貨");
		}

		// 同一個規格出現在多筆明細時先加總再扣；TreeMap 依規格編號由小到大，鎖定與扣庫存都照這個順序
		// detailNumberBySkuId 記這個規格第一次出現在第幾筆明細，錯誤訊息用
		Map<Integer, Integer> quantityBySkuId = new TreeMap<>();
		Map<Integer, Integer> detailNumberBySkuId = new HashMap<>();
		for (int i = 0; i < ordersDetails.size(); i++) {
			OrdersDetail ordersDetail = ordersDetails.get(i);
			int detailNumber = i + 1;
			if (ordersDetail.getSkuId() == null) {
				throw new IllegalArgumentException("第 " + detailNumber + " 筆明細沒有商品規格編號，無法出貨");
			}
			if (ordersDetail.getOrdersQuantity() == null || ordersDetail.getOrdersQuantity() <= 0) {
				throw new IllegalArgumentException("第 " + detailNumber + " 筆明細的訂購數量不正確，無法出貨");
			}
			quantityBySkuId.merge(ordersDetail.getSkuId(), ordersDetail.getOrdersQuantity(), Integer::sum);
			detailNumberBySkuId.putIfAbsent(ordersDetail.getSkuId(), detailNumber);
		}

		// 鎖住要扣庫存的規格，鎖住之後讀到的庫存才是準的
		Map<Integer, ProductSku> skuBySkuId = new HashMap<>();
		for (ProductSku productSku : skuStockRepository.lockSkus(quantityBySkuId.keySet())) {
			skuBySkuId.put(productSku.getSkuId(), productSku);
		}

		for (Map.Entry<Integer, Integer> quantityEntry : quantityBySkuId.entrySet()) {
			Integer skuId = quantityEntry.getKey();
			Integer quantity = quantityEntry.getValue();
			int detailNumber = detailNumberBySkuId.get(skuId);

			ProductSku productSku = skuBySkuId.get(skuId);
			if (productSku == null) {
				throw new IllegalArgumentException("第 " + detailNumber + " 筆明細的商品規格（編號 " + skuId + "）不存在，無法出貨");
			}
			// 庫存不夠時 shipStock 不會更新任何一筆
			if (skuStockRepository.shipStock(skuId, quantity) == 0) {
				int stock = productSku.getStock() == null ? 0 : productSku.getStock();
				throw new IllegalArgumentException("第 " + detailNumber + " 筆（" + productSku.getDisplayName() + "）庫存不足：庫存 "
						+ stock + "，需要 " + quantity);
			}
		}

		// 只更新狀態一個欄位；前面已經鎖住並確認是待出貨，正常不會更新到 0 筆
		if (ordersRepository.updateStatusToShipped(ordersId) == 0) {
			throw new IllegalArgumentException("訂單 " + ordersId + " 已經不是待出貨，無法出貨");
		}
	}

}
