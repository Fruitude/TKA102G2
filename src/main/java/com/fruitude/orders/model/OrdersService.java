package com.fruitude.orders.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fruitude.member.model.MemberAssetService;
import com.fruitude.member.model.MemberVO;
import com.fruitude.product.model.FrontCatalogService;
import com.fruitude.promo.model.PromoService;
import com.fruitude.utils.PostalCodeLookup;
import com.fruitude.utils.PostalCodes;
import com.fruitude.utils.Utils;

import jakarta.persistence.Tuple;

@Service
public class OrdersService {
	@Autowired
	private OrdersRepository ordersRepository;

	@Autowired
	private OrdersDetailRepository ordersDetailRepository;

	@Autowired
	private MemberRepository memberRepository;

	// 下單時鎖定規格、原子扣庫存（庫存夠才扣）
	@Autowired
	private SkuStockRepository skuStockRepository;

	// 結帳頁「收件者同會員」：會員的預設電話、預設地址
	@Autowired
	private MemberAssetService memberAssetService;
	@Autowired
	private FrontCatalogService frontCatalogService;
	@Autowired
	private PromoService promoService;

	private static final String STATUS = "status";
	private static final String PHONE = "phone";
	private static final String RECEIVER = "receiver";
	private static final String MEMBER = "member";
	private static final String ID = "id";

	private static final Integer INITIAL_ORDERS_STATUS = 0;
	// 跟前台 checkout 頁 cart.js 的 CHECKOUT_SHIPPING_FEE 一致
	private static final int SHIPPING_FEE = 45;

	public List<Orders> findAll() {
		return ordersRepository.findAll();
	}

	public List<Tuple> findAllWithJoin() {
		return ordersRepository.findAllWithJoin();
	}

	// 後台訂單管理可排序的欄位白名單：key（網址參數 sort 的值）→ 查詢裡的屬性。
	// 只接受這裡列出的 key，使用者亂傳的值一律當作沒有排序，避免把任意字串帶進查詢。
	// Orders 的屬性不用加別名（Spring 會自動補 O.）；會員在另一張表，要明確寫 M.memberName。
	// 注意：狀態是依資料庫的狀態碼（0~12）排序，不是依畫面上顯示的文字。
	private static final Map<String, String> SORTABLE = Map.ofEntries(
			Map.entry("ordersId", "ordersId"),
			Map.entry("member", "M.memberName"),
			Map.entry("status", "ordersStatus"),
			Map.entry("postalCode", "postalCode"),
			Map.entry("address", "shippingAddress"),
			Map.entry("payment", "paymentMethod"),
			Map.entry("productTotal", "productTotal"),
			Map.entry("discount", "discount"),
			Map.entry("shippingFee", "shippingFee"),
			Map.entry("shoppingCredit", "shoppingCredit"),
			Map.entry("actualPayment", "actualPaymentAmount"),
			Map.entry("receiver", "receiverName"),
			Map.entry("email", "email"),
			Map.entry("phone", "phoneNumber"),
			Map.entry("logisticsNote", "logisticsNote"),
			Map.entry("ordersNote", "ordersNote"),
			Map.entry("date", "ordersDate"));

	public static boolean isSortable(String sortKey) {
		return sortKey != null && SORTABLE.containsKey(sortKey);
	}

	// 沒指定（或不合法）時：日期新到舊。一律再加上訂單編號當次要排序，
	// 否則同值的資料順序不固定，翻頁時可能重複或漏掉
	private Sort buildSort(String sortKey, String dir) {
		if (!isSortable(sortKey) || !("asc".equals(dir) || "desc".equals(dir))) {
			return Sort.by(Sort.Order.desc("ordersDate"), Sort.Order.desc("ordersId"));
		}
		String property = SORTABLE.get(sortKey);
		Sort.Order main = "desc".equals(dir) ? Sort.Order.desc(property) : Sort.Order.asc(property);
		if ("ordersId".equals(property)) {
			return Sort.by(main);
		}
		return Sort.by(main, Sort.Order.asc("ordersId"));
	}

	// 後台訂單管理搜尋與排序。field：id / member / status / receiver / phone；keyword 空白就等於不搜尋。
	// sortKey 見 SORTABLE，dir 是 asc / desc。page 從 0 開始
	public Page<Tuple> search(String field, String keyword, String sortKey, String dir, int page, int size) {
		String kw = keyword == null ? "" : keyword.trim();
		int ordersId = 0;
		String memberName = "", receiverName = "", phoneNumber = "";
		int statusFilter = 0;
		List<Integer> statuses = List.of(-1);

		if (!kw.isEmpty() && field != null) {
			switch (field) {
			case ID:
				// 不是正整數就不可能有符合的訂單，用 -1 讓結果是空的
				ordersId = kw.matches("\\d{1,9}") ? Integer.parseInt(kw) : -1;
				if (ordersId == 0) {
					ordersId = -1;
				}
				break;
			case MEMBER:
				memberName = kw;
				break;
			case RECEIVER:
				receiverName = kw;
				break;
			case PHONE:
				phoneNumber = kw;
				break;
			case STATUS:
				statusFilter = 1;
				statuses = matchStatusCodes(kw);
				break;
			default:
				break; // 不認得的欄位當作沒有搜尋
			}
		}
		return ordersRepository.search(ordersId, memberName, receiverName, phoneNumber, statusFilter, statuses,
				PageRequest.of(page, size, buildSort(sortKey, dir)));
	}

	// 狀態欄位畫面上顯示的是文字（OrderStatus.displayStatus，例如「待出貨」），所以輸入文字就找
	// 顯示文字含有它的所有狀態碼；輸入的是數字就直接當狀態碼。都找不到回 [-1]，結果會是空的
	private List<Integer> matchStatusCodes(String keyword) {
		List<Integer> codes = new ArrayList<>();
		if (keyword.matches("\\d{1,2}")) {
			int code = Integer.parseInt(keyword);
			for (Utils.OrderStatus s : Utils.OrderStatus.values()) {
				if (s.getCode() == code) {
					codes.add(code);
				}
			}
		} else {
			for (Utils.OrderStatus s : Utils.OrderStatus.values()) {
				if (s.getDisplayStatus().contains(keyword)) {
					codes.add(s.getCode());
				}
			}
		}
		return codes.isEmpty() ? List.of(-1) : codes;
	}

	public Optional<Orders> findById(Integer id) {
		return ordersRepository.findById(id);
	}

	public List<MemberVO> getMemberList() {
		return memberRepository.findAll();
	}

	public boolean insert(Orders entity) {
		try {
			entity.setOrdersId(null); // 交給資料庫自動編號
			if (entity.getOrdersDate() == null) {
				entity.setOrdersDate(LocalDateTime.now());
			}
			ordersRepository.save(entity);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean delete(Integer ordersId) {
		if (!ordersRepository.existsById(ordersId)) {
			return false;
		}
		ordersRepository.deleteById(ordersId);
		return true;
	}

	// 先查出既有的訂單，只覆蓋表單可以修改的欄位（ordersId、ordersDate 不動）
	public boolean updateOrders(Integer ordersId, Orders form) {
		Optional<Orders> optional = ordersRepository.findById(ordersId);
		if (optional.isEmpty()) {
			return false;
		}
		Orders orders = optional.get();
		orders.setMemberId(form.getMemberId());
		orders.setShippingAddress(form.getShippingAddress());
		orders.setPaymentMethod(form.getPaymentMethod());
		orders.setProductTotal(form.getProductTotal());
		orders.setDiscount(form.getDiscount());
		orders.setShippingFee(form.getShippingFee());
		orders.setShoppingCredit(form.getShoppingCredit());
		orders.setActualPaymentAmount(form.getActualPaymentAmount());
		orders.setReceiverName(form.getReceiverName());
		orders.setEmail(form.getEmail());
		orders.setPhoneNumber(form.getPhoneNumber());
		orders.setLogisticsNote(form.getLogisticsNote());
		orders.setOrdersNote(form.getOrdersNote());
		orders.setOrdersStatus(form.getOrdersStatus());
		orders.setEmployeeId(form.getEmployeeId());
		ordersRepository.save(orders);
		return true;
	}

	// 做法一：先查出既有的 entity，只改 ordersStatus，再存回去
	public boolean updateStatusByLoad(Integer ordersId, Integer ordersStatus) {
		Optional<Orders> optional = ordersRepository.findById(ordersId);
		if (optional.isEmpty()) {
			return false;
		}
		Orders orders = optional.get();
		orders.setOrdersStatus(ordersStatus);
		ordersRepository.save(orders);
		return true;
	}

	// 做法二：直接下 JPQL UPDATE，只改 ordersStatus
	@Transactional
	public boolean updateStatusByQuery(Integer ordersId, Integer ordersStatus) {
		int updatedRows = ordersRepository.updateStatus(ordersId, ordersStatus);
		return updatedRows > 0;
	}

	// 會員「購買清單」頁：查出會員的訂單與商品明細。
	// 分頁與顯示文字由「訂單出貨狀態碼」合併金流狀態決定（OrderStatus 的對照表）；
	// 狀態碼不在對照表內的訂單不顯示
	public List<MemberOrderView> findMemberOrders(Integer memberId) {
		List<Orders> ordersList = ordersRepository.findByMemberIdOrderByOrdersIdDesc(memberId);
		List<MemberOrderView> result = new ArrayList<>();
		if (ordersList.isEmpty()) {
			return result;
		}
		List<Integer> ordersIds = new ArrayList<>();
		for (Orders o : ordersList) {
			ordersIds.add(o.getOrdersId());
		}
		// 一次查出全部明細，再依訂單編號分組，避免每張訂單各查一次
		Map<Integer, List<MemberOrderView.Item>> itemsByOrders = new HashMap<>();
		for (OrdersDetail d : ordersDetailRepository.findByOrdersIdIn(ordersIds)) {
			itemsByOrders.computeIfAbsent(d.getOrdersId(), k -> new ArrayList<>())
					.add(new MemberOrderView.Item(d.getSkuId(), d.getProductName(), d.getOrdersQuantity()));
		}
		for (Orders o : ordersList) {
			Utils.OrderStatus orderStatus = o.getOrdersStatus() == null ? null
					: Utils.OrderStatus.fromCode(o.getOrdersStatus());
			if (orderStatus == null) {
				continue;
			}
			String status = Utils.getOrderStatus(orderStatus, orderStatus.getExpectedPaymentStatus());
			List<MemberOrderView.Item> items = itemsByOrders.getOrDefault(o.getOrdersId(), List.of());
			result.add(new MemberOrderView(o.getOrdersId(), o.getOrdersDate(), orderStatus.getMemberTab().getKey(),
					status, o.getActualPaymentAmount(), items));
		}
		return result;
	}

	// 結帳頁「收件者同會員」要帶入的資料：會員姓名、Email、預設電話、預設地址，
	// 以及由預設地址算出的郵遞區號（認不出縣市或鄉鎮市區就是 null，由使用者自己填）。
	// 沒有設預設的電話或地址時，退而求其次用第一筆；完全沒有就是 null
	public MemberCheckoutDefaults findMemberCheckoutDefaults(Integer memberId) {
		MemberVO member = memberRepository.findById(memberId).orElse(null);
		if (member == null) {
			return MemberCheckoutDefaults.EMPTY;
		}
		String phone = null;
		List<com.fruitude.member.model.MemberPhone> phones = memberAssetService.findPhones(memberId);
		for (com.fruitude.member.model.MemberPhone p : phones) {
			if (phone == null || Byte.valueOf((byte) 1).equals(p.getDefaultValue())) {
				phone = p.getContactPhone();
				if (Byte.valueOf((byte) 1).equals(p.getDefaultValue())) {
					break;
				}
			}
		}
		String address = null;
		List<com.fruitude.member.model.MemberAddress> addresses = memberAssetService.findAddresses(memberId);
		for (com.fruitude.member.model.MemberAddress a : addresses) {
			if (address == null || Byte.valueOf((byte) 1).equals(a.getDefaultValue())) {
				address = a.getContactAddress();
				if (Byte.valueOf((byte) 1).equals(a.getDefaultValue())) {
					break;
				}
			}
		}
		return new MemberCheckoutDefaults(member.getMemberName(), member.getMemberEmail(), phone, address,
				PostalCodeLookup.fromAddress(address));
	}

	// 會員目前的購物金餘額（元）；找不到會員或餘額是空的就是 0
	public int getShoppingCredit(Integer memberId) {
		MemberVO member = memberRepository.findById(memberId).orElse(null);
		return member == null || member.getShoppingCredit() == null ? 0 : member.getShoppingCredit();
	}

	// 給結帳頁與確認頁預覽用：依「規格編號 → 數量」用資料庫即時價格算商品折扣，不採用前端算的金額。
	// 計算方式和下單時一樣：全館折扣、壽星月、新會員首購和指定商品活動價擇優
	public com.fruitude.promo.model.ProductDiscount previewProductDiscount(Integer memberId, Map<Integer, Integer> qtyBySku) {
		List<com.fruitude.promo.model.DiscountLine> lines = new ArrayList<>();
		for (com.fruitude.product.model.FrontCatalogService.LiveSku sku
				: frontCatalogService.getLiveSkus(new ArrayList<>(qtyBySku.keySet()))) {
			if (sku.available()) {
				int original = sku.originalPrice() != null ? sku.originalPrice() : sku.price();
				lines.add(new com.fruitude.promo.model.DiscountLine(sku.price(), original, qtyBySku.get(sku.skuId())));
			}
		}
		return findProductDiscount(memberId, lines);
	}

	// 會員生日的月份是不是現在這個月（壽星月資格）
	public boolean isBirthdayMonth(Integer memberId) {
		MemberVO member = memberRepository.findById(memberId).orElse(null);
		return member != null && member.getMemberBirthday() != null
				&& member.getMemberBirthday().getMonth() == LocalDate.now().getMonth();
	}

	// 商品折扣（全館折扣、壽星月、新會員首購，彼此只套用折扣最大的一個，並且和指定商品活動價擇優）。
	// 回傳的折扣金額是相對於畫面上小計（已經是活動價）再多折的金額。
	// 壽星月：會員生日的月份等於現在的月份；新會員首購：這個會員還沒有任何訂單
	public com.fruitude.promo.model.ProductDiscount findProductDiscount(Integer memberId,
			List<com.fruitude.promo.model.DiscountLine> lines) {
		boolean isFirstOrder = ordersRepository.countByMemberId(memberId) == 0;
		boolean isBirthdayMonth = false;
		MemberVO member = memberRepository.findById(memberId).orElse(null);
		if (member != null && member.getMemberBirthday() != null) {
			isBirthdayMonth = member.getMemberBirthday().getMonth() == LocalDate.now().getMonth();
		}
		return promoService.calcProductDiscount(isBirthdayMonth, isFirstOrder, lines);
	}

	// 把結帳頁送來的 CheckoutForm 轉成訂單主檔 Orders（還沒存檔）
	// memberId 是目前登入的會員（由 Controller 從 session 取得，不接受前端自己傳）
	public Orders toOrders(CheckoutForm form, Integer storeCredit, Integer memberId) {
		Orders orders = new Orders();
		orders.setMemberId(memberId);
		orders.setReceiverName(form.getReceiverName());
		orders.setEmail(form.getEmail());
		orders.setPhoneNumber(form.getPhoneNumber());
		orders.setPostalCode(PostalCodes.toInteger(form.getShippingZip()));
		orders.setShippingAddress(form.getShippingAddress());
		orders.setPaymentMethod(form.getPaymentMethod());
		orders.setLogisticsNote(form.getLogisticsNote());
		orders.setOrdersNote(form.getOrderNote());

		// placeOrder 已用資料庫即時價格驗證品項，再加總商品金額。
		// 指定商品促銷的品項已經是活動價；全館折扣、壽星月、新會員首購要和活動價擇優，所以每個品項都要帶原價
		int productTotal = 0;
		List<com.fruitude.promo.model.DiscountLine> discountLines = new ArrayList<>();
		for (CheckoutItem item : form.getItems()) {
			productTotal += item.getPrice() * item.getQty();
			int original = item.getOriginalPrice() != null ? item.getOriginalPrice() : item.getPrice();
			discountLines.add(new com.fruitude.promo.model.DiscountLine(item.getPrice(), original, item.getQty()));
		}

		// 滿額免運：有進行中的活動且商品金額達門檻，就把運費折抵掉（算進 discount，運費欄位仍記原本的運費）。
		// 以伺服器重新計算為準，不信任前端畫面上顯示的金額
		int freeShippingThreshold = promoService.findFreeShippingThreshold();
		int discount = (freeShippingThreshold > 0 && productTotal >= freeShippingThreshold) ? SHIPPING_FEE : 0;
		// 商品折扣：全館折扣、壽星月、新會員首購，同一筆訂單只套用折扣最大的一個，並且和指定商品活動價擇優
		discount += findProductDiscount(memberId, discountLines).amount();
		int beforeCredit = Math.max(0, productTotal + SHIPPING_FEE - discount);
		// 不能讓購物金折抵超過應付金額，也不能是負數
		int shoppingCredit = storeCredit == null ? 0 : Math.min(Math.max(storeCredit, 0), beforeCredit);

		orders.setProductTotal(productTotal);
		orders.setDiscount(discount);
		orders.setShippingFee(SHIPPING_FEE);
		orders.setShoppingCredit(shoppingCredit);
		orders.setActualPaymentAmount(beforeCredit - shoppingCredit);
		orders.setOrdersDate(LocalDateTime.now());
		orders.setOrdersStatus(INITIAL_ORDERS_STATUS); // TODO 跟組員確認「剛下單」該用哪個代碼
		return orders;
	}

	// 把購物車品項轉成訂單明細 OrdersDetail（還沒存檔）。savedOrders 一定要是存檔後、
	// 已經有 ordersId 的物件，不然明細會存成 orders_id = null
	public List<OrdersDetail> toOrdersDetails(Orders savedOrders, List<CheckoutItem> items, String invoiceCarrier) {
		List<OrdersDetail> details = new ArrayList<>();
		for (CheckoutItem item : items) {
			OrdersDetail detail = new OrdersDetail();
			detail.setOrdersId(savedOrders.getOrdersId());
			detail.setSkuId(item.getSkuId());
			detail.setProductName(item.getProductName());
			detail.setOrdersQuantity(item.getQty());
			// 品項價格已在 placeOrder 驗證，確認畫面與訂單金額一致。
			detail.setUnitPrice(item.getPrice());
			detail.setInvoiceCarrier(invoiceCarrier);
			details.add(detail);
		}
		return details;
	}

	// 結帳頁「確認付款」的進入點：主檔、明細一起存，其中一個失敗就整筆回滾，
	// 不會留下沒有明細的訂單
	@Transactional
	public Orders placeOrder(CheckoutForm form, Integer storeCredit, Integer memberId) {
		// 第一步：先鎖定這次要買的規格（依 sku_id 由小到大），鎖會一直持有到這個交易結束。
		// 之後的驗證與扣庫存都在鎖裡面進行：別的訂單不能同時改這些規格的庫存，
		// 前台讀庫存（getLiveSkus 等，用 FOR SHARE）也要等這個交易結束才讀得到，不會讀到做到一半的數字
		Map<Integer, Integer> qtyBySku = new TreeMap<>();
		if (form.getItems() != null) {
			for (CheckoutItem item : form.getItems()) {
				if (item != null && item.getSkuId() != null && item.getQty() != null && item.getQty() > 0) {
					qtyBySku.merge(item.getSkuId(), item.getQty(), Integer::sum);
				}
			}
		}
		if (!qtyBySku.isEmpty()) {
			skuStockRepository.lockSkus(qtyBySku.keySet());
		}
		frontCatalogService.validateCheckoutItems(form.getItems());
		Orders orders = toOrders(form, storeCredit, memberId);
		// 第二步：原子扣庫存。stock 扣掉訂購數量、outbound_qty 加上訂購數量，「庫存夠才扣」的檢查寫在同一個 UPDATE 裡；
		// 任何一個規格扣不下去就丟例外，整筆交易回滾（先扣成功的規格、購物金都會還原，不會留下訂單）
		for (Map.Entry<Integer, Integer> entry : qtyBySku.entrySet()) {
			if (skuStockRepository.deductStock(entry.getKey(), entry.getValue()) == 0) {
				throw new com.fruitude.product.model.ProductUnavailableException(
						productNameOf(form.getItems(), entry.getKey()) + " 庫存不足，請返回購物車調整數量");
			}
		}
		// 實際折抵的購物金要從會員餘額扣掉；餘額不足就丟例外，整筆交易回滾，不會留下訂單
		int usedCredit = orders.getShoppingCredit();
		if (usedCredit > 0 && memberRepository.deductShoppingCredit(orders.getMemberId(), usedCredit) == 0) {
			throw new InsufficientCreditException("購物金餘額不足，請調整折抵金額");
		}
		ordersRepository.save(orders); // 先存，拿到自動產生的 ordersId
		List<OrdersDetail> details = toOrdersDetails(orders, form.getItems(), form.getInvoiceCarrier());
		ordersDetailRepository.saveAll(details);
		clearCatalogCacheAfterCommit(); // 庫存變了，等交易 commit 之後前台商品列表快取要重新載入
		return orders;
	}
	
	// 錯誤訊息用：找出這個規格在購物車品項裡的商品名稱（validateCheckoutItems 已依資料庫填好）
	private String productNameOf(List<CheckoutItem> items, Integer skuId) {
		for (CheckoutItem item : items) {
			if (item != null && skuId.equals(item.getSkuId()) && item.getProductName() != null) {
				return item.getProductName();
			}
		}
		return "商品";
	}

	// 庫存變了，前台商品列表的快取要重新載入（等交易 commit 之後才清，沒清到就在 10 分鐘快取過期時更新）
	private void clearCatalogCacheAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					frontCatalogService.clearCache();
				}
			});
		} else {
			frontCatalogService.clearCache();
		}
	}

	//####################  後台 ################################
	public List<Tuple> getOrderDetailById(Integer orderId) {
		return ordersDetailRepository.getDetail(orderId);
	}
}
