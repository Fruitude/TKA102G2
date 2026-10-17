package com.fruitude.shipping.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.fruitude.employee.model.Employee;
import com.fruitude.employee.model.EmployeeRepository;
import com.fruitude.orders.model.Orders;
import com.fruitude.orders.model.OrdersDetail;
import com.fruitude.product.model.ProductSku;
import com.fruitude.utils.Utils;

@Service
public class ShippingService {

	// 首頁待出貨訂單每頁顯示的筆數
	private static final int PAGE_SIZE = 10;

	// 訂單日期由舊到新，同一時間的再依訂單編號由小到大
	private static final Sort PENDING_ORDERS_SORT = Sort.by("ordersDate", "ordersId");

	// 歷史出貨紀錄不列的狀態：0 待出貨、2 非客戶端問題取消、4 客戶端主動取消
	private static final List<Integer> HISTORY_EXCLUDED_STATUSES = List.of(0, 2, 4);

	// 歷史出貨紀錄的排序：訂單日期由新到舊，同一時間的再依訂單編號由大到小
	private static final Sort HISTORY_ORDERS_SORT = Sort.by(Sort.Direction.DESC, "ordersDate", "ordersId");

	// 歷史出貨紀錄只填一邊日期時，另一邊用的日期，等於那一邊不限（兩邊都沒填時不篩選日期，用不到這兩個）
	// 迄日用 12/30：查詢時會再加一天，12/31 加一天會超過 MySQL DATETIME 的上限
	private static final LocalDate HISTORY_MIN_DATE = LocalDate.of(1900, 1, 1);
	private static final LocalDate HISTORY_MAX_DATE = LocalDate.of(9999, 12, 30);

	@Autowired
	private ShippingOrderRepository ordersRepository;

	@Autowired
	private ShippingOrdersDetailRepository ordersDetailRepository;

	@Autowired
	private ShippingSkuStockRepository skuStockRepository;

	@Autowired
	private EmployeeRepository employeeRepository;

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

	// 歷史出貨紀錄的其中一頁：狀態不是待出貨（0）、也不是取消（2、4）的訂單，再依查詢條件篩選，訂單日期由新到舊
	// page 的規則和 getPendingOrders 相同
	// 查詢條件都可以不填（null 或空白）：ordersId 完全相同、ordersStatus 完全相同、receiverName 找部分文字
	// startDate / endDate 是訂單日期的區間，兩天都包含在內；起日晚於迄日由 ShippingController 的 validateHistorySearch 擋下
	public Page<Orders> getHistoryOrders(int page, Integer ordersId, Integer ordersStatus, String receiverName,
			LocalDate startDate, LocalDate endDate) {
		// 沒填的條件換成「不篩選」的值，對應 ShippingOrderRepository 的 searchHistoryOrders
		int ordersIdFilter = ordersId == null ? 0 : ordersId;
		int ordersStatusFilter = ordersStatus == null ? -1 : ordersStatus;
		String receiverNameFilter = receiverName == null ? "" : receiverName.strip();
		// 起日、迄日都沒填時不篩選日期（訂單日期是空的訂單也會列出）；只填一邊時，另一邊用 HISTORY_MIN_DATE / HISTORY_MAX_DATE 補上
		int dateFilter = startDate == null && endDate == null ? 0 : 1;
		LocalDate fromDate = startDate == null ? HISTORY_MIN_DATE : startDate;
		LocalDate toDate = endDate == null ? HISTORY_MAX_DATE : endDate;
		LocalDateTime startDateTime = fromDate.atStartOfDay();
		LocalDateTime endDateTime = toDate.plusDays(1).atStartOfDay(); // 迄日隔天 0 點（不含），迄日當天整天都算

		int pageIndex = Math.max(page, 1) - 1; // PageRequest 的頁碼從 0 開始
		Page<Orders> ordersPage = ordersRepository.searchHistoryOrders(HISTORY_EXCLUDED_STATUSES, ordersIdFilter,
				ordersStatusFilter, receiverNameFilter, dateFilter, startDateTime, endDateTime,
				PageRequest.of(pageIndex, PAGE_SIZE, HISTORY_ORDERS_SORT));
		if (pageIndex > 0 && pageIndex >= ordersPage.getTotalPages()) {
			int lastPageIndex = Math.max(ordersPage.getTotalPages() - 1, 0);
			ordersPage = ordersRepository.searchHistoryOrders(HISTORY_EXCLUDED_STATUSES, ordersIdFilter,
					ordersStatusFilter, receiverNameFilter, dateFilter, startDateTime, endDateTime,
					PageRequest.of(lastPageIndex, PAGE_SIZE, HISTORY_ORDERS_SORT));
		}
		return ordersPage;
	}

	// 歷史出貨紀錄會出現的訂單狀態（排除待出貨與取消），給查詢的狀態下拉選單用，依狀態碼由小到大
	public List<Utils.OrderStatus> getHistoryStatuses() {
		List<Utils.OrderStatus> statuses = new ArrayList<>();
		for (Utils.OrderStatus status : Utils.OrderStatus.values()) {
			if (!HISTORY_EXCLUDED_STATUSES.contains(status.getCode())) {
				statuses.add(status);
			}
		}
		return statuses;
	}

	// 這張訂單會不會出現在歷史出貨紀錄：條件和 getHistoryOrders 相同，歷史單筆頁用來擋下不該顯示的訂單
	public boolean isHistoryOrders(Orders orders) {
		return orders.getOrdersStatus() != null && !HISTORY_EXCLUDED_STATUSES.contains(orders.getOrdersStatus());
	}

	// 單筆訂單，查不到回傳 null
	public Orders getOneOrders(Integer ordersId) {
		return ordersRepository.findById(ordersId).orElse(null);
	}

	// 單筆員工，查不到或沒有員工編號時回傳 null
	public Employee getOneEmployee(Integer employeeId) {
		return employeeId == null ? null : employeeRepository.findById(employeeId).orElse(null);
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

	// 確認出貨：訂單狀態由待出貨（0）改成已出貨（1）並記下出貨員工編號，明細的規格扣掉庫存與預計出貨
	// 整張訂單一次出完，全部寫在同一個交易；任何一項檢查沒過就丟 IllegalArgumentException，訂單與庫存都不會被改到
	@Transactional
	public void shipOrders(Integer ordersId, Integer employeeId) {
		if (employeeId == null) {
			throw new IllegalArgumentException("登入逾時，請重新登入");
		}

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

		// 只更新狀態與出貨員工兩個欄位；前面已經鎖住並確認是待出貨，正常不會更新到 0 筆
		if (ordersRepository.updateStatusToShipped(ordersId, employeeId) == 0) {
			throw new IllegalArgumentException("訂單 " + ordersId + " 已經不是待出貨，無法出貨");
		}
	}

	// 取消出貨：訂單狀態由配送中（1）改回待出貨（0）並清空出貨員工編號，明細的規格把庫存與預計出貨加回去
	// 只有配送中的訂單可以取消；和 shipOrders 一樣全部寫在同一個交易，任何一項檢查沒過就丟 IllegalArgumentException，訂單與庫存都不會被改到
	@Transactional
	public void cancelShipOrders(Integer ordersId) {
		// 先鎖住訂單再看狀態：連按兩次、兩個人同時取消同一張單時，後來的那一個會在這裡被擋下
		Orders orders = ordersRepository.lockOrders(ordersId).orElse(null);
		if (orders == null) {
			throw new IllegalArgumentException("查無此訂單");
		}
		if (Integer.valueOf(0).equals(orders.getOrdersStatus())) {
			throw new IllegalArgumentException("訂單編號-" + ordersId + " 已取消出貨");
		}
		if (!Integer.valueOf(1).equals(orders.getOrdersStatus())) {
			throw new IllegalArgumentException("訂單編號-" + ordersId + " 已經不是配送中，無法取消出貨");
		}

		List<OrdersDetail> ordersDetails = ordersDetailRepository.findByOrdersIdOrderByOrdersDetailIdAsc(ordersId);

		// 同一個規格出現在多筆明細時先加總再加回；TreeMap 依規格編號由小到大，鎖定與加庫存都照這個順序（和 shipOrders 相同）
		// detailNumberBySkuId 記這個規格第一次出現在第幾筆明細，錯誤訊息用
		// 沒有規格編號或訂購數量不正確的明細，出貨時不會扣到庫存，這裡也就不用加回
		Map<Integer, Integer> quantityBySkuId = new TreeMap<>();
		Map<Integer, Integer> detailNumberBySkuId = new HashMap<>();
		for (int i = 0; i < ordersDetails.size(); i++) {
			OrdersDetail ordersDetail = ordersDetails.get(i);
			if (ordersDetail.getSkuId() == null || ordersDetail.getOrdersQuantity() == null
					|| ordersDetail.getOrdersQuantity() <= 0) {
				continue;
			}
			quantityBySkuId.merge(ordersDetail.getSkuId(), ordersDetail.getOrdersQuantity(), Integer::sum);
			detailNumberBySkuId.putIfAbsent(ordersDetail.getSkuId(), i + 1);
		}

		// 鎖住要加回庫存的規格；加回去不用檢查庫存夠不夠，只要確認規格還在
		Map<Integer, ProductSku> skuBySkuId = new HashMap<>();
		for (ProductSku productSku : skuStockRepository.lockSkus(quantityBySkuId.keySet())) {
			skuBySkuId.put(productSku.getSkuId(), productSku);
		}

		for (Map.Entry<Integer, Integer> quantityEntry : quantityBySkuId.entrySet()) {
			Integer skuId = quantityEntry.getKey();
			Integer quantity = quantityEntry.getValue();

			// 規格不存在時 restoreStock 也不會更新任何一筆
			if (skuBySkuId.get(skuId) == null || skuStockRepository.restoreStock(skuId, quantity) == 0) {
				throw new IllegalArgumentException("訂單編號-" + ordersId + " 第 " + detailNumberBySkuId.get(skuId)
						+ " 筆明細的商品規格不存在，無法取消出貨");
			}
		}

		// 只更新狀態與出貨員工兩個欄位；前面已經鎖住並確認是配送中，正常不會更新到 0 筆
		if (ordersRepository.updateStatusToPending(ordersId) == 0) {
			throw new IllegalArgumentException("訂單編號-" + ordersId + " 已經不是配送中，無法取消出貨");
		}
	}

}
