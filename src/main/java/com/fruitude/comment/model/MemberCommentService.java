package com.fruitude.comment.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.orders.model.Orders;
import com.fruitude.orders.model.OrdersDetail;
import com.fruitude.orders.model.OrdersDetailRepository;
import com.fruitude.orders.model.OrdersRepository;
import com.fruitude.orders.model.OrdersService;
import com.fruitude.product.model.Product;
import com.fruitude.product.model.ProductRepository;
import com.fruitude.product.model.ProductSku;
import com.fruitude.product.model.ProductSkuRepository;

// 會員評論：評論存在 orders_detail 的 comment_* 欄位，每張訂單的每個商品只能評論一次（不同訂單的同商品可再評論）
@Service
public class MemberCommentService {

	public static final int MAX_TEXT_LENGTH = 800;
	// 與後台 CommentAdminService.STATUS_VISIBLE 相同：顯示中
	private static final int STATUS_VISIBLE = 1;

	// 評論頁的一個商品：reviewed 表示這筆訂單明細已評論過，已評論的不能再寫
	public record ReviewItem(Integer ordersDetailId, Integer skuId, String productName, Integer quantity,
			Integer unitPrice, boolean reviewed, Integer commentStar, String commentText) {
	}

	/** 重複評論，對應 HTTP 409 */
	public static class DuplicateReviewException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public DuplicateReviewException(String message) {
			super(message);
		}
	}

	@Autowired
	private OrdersRepository ordersRepository;
	@Autowired
	private OrdersDetailRepository ordersDetailRepository;
	@Autowired
	private ProductSkuRepository productSkuRepository;
	@Autowired
	private ProductRepository productRepository;

	// 該會員這張訂單的各商品與評論狀態
	@Transactional(readOnly = true)
	public List<ReviewItem> findOrderItems(Integer memberId, Integer ordersId) {
		Orders orders = findCommentableOrders(memberId, ordersId);
		List<ReviewItem> result = new ArrayList<>();
		List<OrdersDetail> details = ordersDetailRepository.findByOrdersIdIn(List.of(orders.getOrdersId()));
		List<Integer> skuIds = new ArrayList<>();
		for (OrdersDetail d : details) {
			skuIds.add(d.getSkuId());
		}
		// 顯示規格名稱（例如「香水草莓250g一般盒」）；查不到規格才退回訂單明細上的品名
		Map<Integer, String> skuNames = new HashMap<>();
		for (ProductSku sku : productSkuRepository.findAllById(skuIds)) {
			skuNames.put(sku.getSkuId(), ProductSku.resolveDisplayName(sku.getSkuName(), sku.getAnotherName()));
		}
		for (OrdersDetail d : details) {
			boolean reviewed = hasText(d.getCommentText());
			String itemName = skuNames.get(d.getSkuId());
			if (!hasText(itemName)) {
				itemName = d.getProductName();
			}
			result.add(new ReviewItem(d.getOrdersDetailId(), d.getSkuId(), itemName, d.getOrdersQuantity(),
					d.getUnitPrice(), reviewed, reviewed ? d.getCommentStar() : null,
					reviewed ? d.getCommentText() : null));
		}
		return result;
	}

	@Transactional
	public void submit(Integer memberId, Integer ordersId, Integer ordersDetailId, Integer star, String text) {
		Orders orders = findCommentableOrders(memberId, ordersId);
		OrdersDetail detail = ordersDetailRepository.findById(ordersDetailId == null ? -1 : ordersDetailId)
				.orElseThrow(() -> new NoSuchElementException("找不到這個商品"));
		if (!orders.getOrdersId().equals(detail.getOrdersId())) {
			throw new NoSuchElementException("找不到這個商品");
		}
		if (star == null || star < 1 || star > 5) {
			throw new IllegalArgumentException("請選擇 1 到 5 顆星");
		}
		String content = text == null ? "" : text.trim();
		if (content.isEmpty()) {
			throw new IllegalArgumentException("請輸入評論內容");
		}
		if (content.length() > MAX_TEXT_LENGTH) {
			throw new IllegalArgumentException("評論內容最多 " + MAX_TEXT_LENGTH + " 字");
		}
		if (hasText(detail.getCommentText())) {
			throw new DuplicateReviewException("這張訂單的這個商品您已經評論過了");
		}
		detail.setCommentStar(star);
		detail.setCommentText(content);
		detail.setCommentDate(LocalDateTime.now());
		detail.setCommentStatus(STATUS_VISIBLE);
		ordersDetailRepository.save(detail);
		addToStatistics(detail.getSkuId(), star);
	}

	// 累計商品規格與商品的評論數、星數總和（平均 = 星數 / 評論數）
	private void addToStatistics(Integer skuId, int star) {
		ProductSku sku = productSkuRepository.findById(skuId).orElse(null);
		if (sku == null) {
			return;
		}
		sku.setAllCommentAmount(nz(sku.getAllCommentAmount()) + 1);
		sku.setAllCommentStar(nz(sku.getAllCommentStar()) + star);
		productSkuRepository.save(sku);
		Product product = sku.getProduct();
		if (product != null) {
			product.setAllCommentAmount(nz(product.getAllCommentAmount()) + 1);
			product.setAllCommentStar(nz(product.getAllCommentStar()) + star);
			productRepository.save(product);
		}
	}

	// 訂單必須是該會員的，且狀態為已配送／已驗收；否則一律當作找不到，不洩漏他人訂單
	private Orders findCommentableOrders(Integer memberId, Integer ordersId) {
		Orders orders = ordersRepository.findById(ordersId == null ? -1 : ordersId)
				.orElseThrow(() -> new NoSuchElementException("找不到這張訂單"));
		if (!memberId.equals(orders.getMemberId())) {
			throw new NoSuchElementException("找不到這張訂單");
		}
		if (!OrdersService.isCommentableStatus(orders.getOrdersStatus())) {
			throw new IllegalArgumentException("這張訂單目前還不能評論");
		}
		return orders;
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
