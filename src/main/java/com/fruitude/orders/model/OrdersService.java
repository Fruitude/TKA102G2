package com.fruitude.orders.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.member.model.MemberVO;
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
	@Autowired
	private com.fruitude.product.model.FrontCatalogService frontCatalogService;
	@Autowired
	private com.fruitude.promo.model.PromoService promoService;

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

	// 給結帳頁與確認頁預覽用：依「規格編號 → 數量」用資料庫即時價格算商品折扣，不採用前端算的金額。
	// 計算方式和下單時一樣：活動價的品項不算進折扣基準
	public com.fruitude.promo.model.ProductDiscount previewProductDiscount(Integer memberId, Map<Integer, Integer> qtyBySku) {
		int discountBase = 0;
		for (com.fruitude.product.model.FrontCatalogService.LiveSku sku
				: frontCatalogService.getLiveSkus(new ArrayList<>(qtyBySku.keySet()))) {
			boolean onPromo = sku.originalPrice() != null && sku.price() != null && sku.price() < sku.originalPrice();
			if (sku.available() && !onPromo) {
				discountBase += sku.price() * qtyBySku.get(sku.skuId());
			}
		}
		return findProductDiscount(memberId, discountBase);
	}

	// 商品折扣金額（全館折扣、壽星月、新會員首購，只套用折扣最大的一個）。
	// 壽星月：會員生日的月份等於現在的月份；新會員首購：這個會員還沒有任何訂單
	public com.fruitude.promo.model.ProductDiscount findProductDiscount(Integer memberId, int productTotal) {
		boolean isFirstOrder = ordersRepository.countByMemberId(memberId) == 0;
		boolean isBirthdayMonth = false;
		MemberVO member = memberRepository.findById(memberId).orElse(null);
		if (member != null && member.getMemberBirthday() != null) {
			isBirthdayMonth = member.getMemberBirthday().getMonth() == LocalDate.now().getMonth();
		}
		return promoService.calcProductDiscount(isBirthdayMonth, isFirstOrder, productTotal);
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
		// 指定商品促銷的品項已經是活動價，不再算進全館折扣、壽星月、新會員首購的折扣基準（discountBase）
		int productTotal = 0;
		int discountBase = 0;
		for (CheckoutItem item : form.getItems()) {
			int line = item.getPrice() * item.getQty();
			productTotal += line;
			if (!item.isOnPromoPrice()) {
				discountBase += line;
			}
		}

		// 滿額免運：有進行中的活動且商品金額達門檻，就把運費折抵掉（算進 discount，運費欄位仍記原本的運費）。
		// 以伺服器重新計算為準，不信任前端畫面上顯示的金額
		int freeShippingThreshold = promoService.findFreeShippingThreshold();
		int discount = (freeShippingThreshold > 0 && productTotal >= freeShippingThreshold) ? SHIPPING_FEE : 0;
		// 商品折扣：全館折扣、壽星月、新會員首購，同一筆訂單只套用折扣最大的一個
		discount += findProductDiscount(memberId, discountBase).amount();
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
		frontCatalogService.validateCheckoutItems(form.getItems());
		Orders orders = toOrders(form, storeCredit, memberId);
		// 實際折抵的購物金要從會員餘額扣掉；餘額不足就丟例外，整筆交易回滾，不會留下訂單
		int usedCredit = orders.getShoppingCredit();
		if (usedCredit > 0 && memberRepository.deductShoppingCredit(orders.getMemberId(), usedCredit) == 0) {
			throw new InsufficientCreditException("購物金餘額不足，請調整折抵金額");
		}
		ordersRepository.save(orders); // 先存，拿到自動產生的 ordersId
		List<OrdersDetail> details = toOrdersDetails(orders, form.getItems(), form.getInvoiceCarrier());
		ordersDetailRepository.saveAll(details);
		return orders;
	}
	
	//####################  後台 ################################
	public List<Tuple> getOrderDetailById(Integer orderId) {
		return ordersDetailRepository.getDetail(orderId);
	}
}
