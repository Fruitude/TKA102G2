package com.fruitude.member.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 會員購物車服務：資料庫只保存規格、數量與勾選狀態，
 * 瀏覽器端的快取（價格、名稱、庫存）仍由 cart.js 向商品資料重新驗證。
 */
@Service
public class ShoppingCartService {

	// 與前端一致：單一規格數量上限。
	private static final int MAX_QTY = 999;
	// 避免惡意請求塞入過多品項。
	private static final int MAX_ITEMS = 200;

	private final ShoppingCartRepository repository;

	public ShoppingCartService(ShoppingCartRepository repository) {
		this.repository = repository;
	}

	/** 回傳購物車一列的資料；imageId 可能為 null。 */
	public record CartLine(Integer skuId, int qty, boolean checked, Integer imageId) {}

	/** 前端送來的一筆購物車內容。 */
	public record CartInput(Integer skuId, Integer qty, Boolean checked) {}

	@Transactional(readOnly = true)
	public List<CartLine> findCart(Integer memberId) {
		List<ShoppingCart> rows = repository.findByMemberIdOrderByCartIdAsc(memberId);
		List<Integer> skuIds = new ArrayList<>();
		for (ShoppingCart row : rows) {
			skuIds.add(row.getSkuId());
		}
		Map<Integer, Integer> images = new HashMap<>();
		if (!skuIds.isEmpty()) {
			for (ShoppingCartRepository.SkuImageRow image : repository.findFirstImageIds(skuIds)) {
				images.put(image.getSkuId(), image.getImageId());
			}
		}
		List<CartLine> lines = new ArrayList<>();
		for (ShoppingCart row : rows) {
			lines.add(new CartLine(row.getSkuId(), row.getQty() == null ? 0 : row.getQty(),
					row.getChecked() == null || row.getChecked() == 1, images.get(row.getSkuId())));
		}
		return lines;
	}

	/** 以前端送來的完整清單取代該會員的購物車；同規格數量會合併，不合法的項目直接略過。 */
	@Transactional
	public List<CartLine> replaceCart(Integer memberId, List<CartInput> inputs) {
		Map<Integer, ShoppingCart> merged = new LinkedHashMap<>();
		if (inputs != null) {
			for (CartInput input : inputs) {
				if (input == null || input.skuId() == null || input.skuId() <= 0 || input.qty() == null
						|| input.qty() <= 0) {
					continue;
				}
				ShoppingCart row = merged.get(input.skuId());
				if (row == null) {
					if (merged.size() >= MAX_ITEMS) {
						continue;
					}
					row = new ShoppingCart();
					row.setMemberId(memberId);
					row.setSkuId(input.skuId());
					row.setQty(0);
					merged.put(input.skuId(), row);
				}
				row.setQty(Math.min(MAX_QTY, row.getQty() + input.qty()));
				// 同規格重複出現時，只要有一筆勾選就視為勾選。
				boolean checked = input.checked() == null || input.checked();
				if (row.getChecked() == null || row.getChecked() == 0) {
					row.setChecked((byte) (checked ? 1 : 0));
				}
			}
		}

		repository.deleteAllByMember(memberId);
		if (!merged.isEmpty()) {
			List<Integer> existing = repository.findExistingSkuIds(new ArrayList<>(merged.keySet()));
			List<ShoppingCart> toSave = new ArrayList<>();
			for (ShoppingCart row : merged.values()) {
				if (existing.contains(row.getSkuId())) {
					toSave.add(row);
				}
			}
			repository.saveAll(toSave);
		}
		return findCart(memberId);
	}

	@Transactional
	public void clear(Integer memberId) {
		repository.deleteAllByMember(memberId);
	}
}
