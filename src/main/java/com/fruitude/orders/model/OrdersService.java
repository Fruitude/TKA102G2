package com.fruitude.orders.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	private static final String STATUS = "status";
	private static final String PHONE = "phone";
	private static final String RECEIVER = "receiver";
	private static final String MEMBER = "member";
	private static final String ID = "id";

	// 目前沒有登入機制、也沒有折扣功能，先用固定值頂著；
	// 之後有登入、折扣功能時，把這兩個地方換成真正的邏輯即可
	private static final Integer DEFAULT_MEMBER_ID = 1;
	private static final Integer INITIAL_ORDERS_STATUS = 0;
	// 跟前台 checkout 頁 cart.js 的 CHECKOUT_SHIPPING_FEE 一致
	private static final int SHIPPING_FEE = 45;

	public List<Orders> findAll() {
		return ordersRepository.findAll();
	}

	public List<Tuple> findAllWithJoin() {
		return ordersRepository.findAllWithJoin();
	}

	// 後台訂單管理搜尋。field：id / member / status / receiver / phone；keyword 空白就等於不搜尋。
	// page 從 0 開始
	public Page<Tuple> search(String field, String keyword, int page, int size) {
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
				PageRequest.of(page, size));
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

	public List<Member> getMemberList() {
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

	// 把結帳頁送來的 CheckoutForm 轉成訂單主檔 Orders（還沒存檔）
	public Orders toOrders(CheckoutForm form, Integer storeCredit) {
		Orders orders = new Orders();
		orders.setMemberId(DEFAULT_MEMBER_ID); // TODO 等登入功能做好，改成目前登入會員的 id
		orders.setReceiverName(form.getReceiverName());
		orders.setEmail(form.getEmail());
		orders.setPhoneNumber(form.getPhoneNumber());
		orders.setPostalCode(PostalCodes.toInteger(form.getShippingZip()));
		orders.setShippingAddress(form.getShippingAddress());
		orders.setPaymentMethod(form.getPaymentMethod());
		orders.setLogisticsNote(form.getLogisticsNote());
		orders.setOrdersNote(form.getOrderNote());

		// TODO 這裡先用前端傳來的 price 加總；之後有商品資料表時，
		// 要改成用 skuId 查詢資料庫目前的價格，不能信任前端傳來的值
		int productTotal = 0;
		for (CheckoutItem item : form.getItems()) {
			productTotal += item.getPrice() * item.getQty();
		}

		int discount = 0; // 目前沒有折扣機制
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
			// TODO 之後有商品資料表時，改成查詢資料庫目前的價格，不能信任前端傳來的值
			detail.setUnitPrice(item.getPrice());
			detail.setInvoiceCarrier(invoiceCarrier);
			details.add(detail);
		}
		return details;
	}

	// 結帳頁「確認付款」的進入點：主檔、明細一起存，其中一個失敗就整筆回滾，
	// 不會留下沒有明細的訂單
	@Transactional
	public Orders placeOrder(CheckoutForm form, Integer storeCredit) {
		Orders orders = toOrders(form, storeCredit);
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
