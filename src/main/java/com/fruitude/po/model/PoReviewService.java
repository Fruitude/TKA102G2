package com.fruitude.po.model;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.podetail.model.PoDetailVO;
import com.fruitude.product.model.ProductSku;
import com.fruitude.vendor.model.VendorVO;

// 採購單審核的資料操作：repository 和 PoService 共用，處理待審核採購單的審核
@Service
public class PoReviewService {

	@Autowired
	private PoRepository poRepository;

	@Autowired
	private PoSkuStockRepository poSkuStockRepository;

	// 待審核（poStatus = 0）的採購單，採購單審核首頁列出這些單
	public List<PoVO> getPendingReviewPos() {
		return poRepository.getByPoStatus((byte) 0);
	}

	// 審核頁的單筆資料：查不到時回傳 null；審核完會回到同一頁，所以不是待審核的採購單也查得到
	public PoVO getOnePo(Integer poId) {
		return poRepository.findById(poId).orElse(null);
	}

	// 採購單內容的摘要：總金額，加上每筆明細的規格編號、採購數量、進貨單價（依規格編號由小到大）
	// 審核頁顯示時算一次放在表單裡，按下通過時再以資料庫當下的內容算一次，兩個不一樣就代表審核頁開著的期間採購單被修改過
	public String getReviewedContent(PoVO poVO) {
		Map<Integer, String> detailBySkuId = new TreeMap<>();
		if (poVO.getPoDetails() != null) {
			for (PoDetailVO poDetailVO : poVO.getPoDetails()) {
				detailBySkuId.put(poDetailVO.getSkuId().getSkuId(),
						poDetailVO.getQuantity() + "x" + poDetailVO.getUnitPrice());
			}
		}
		return poVO.getTotalAmount() + ";" + detailBySkuId;
	}

	// 審核通過：採購單狀態改為申請通過（1），並把每筆明細的採購數量加到對應規格的待進貨
	// 兩件事在同一個交易裡，任何一步丟出例外，採購單狀態和已經加上去的待進貨都會還原
	// reviewedContent 是審核頁顯示當時的內容摘要（getReviewedContent），和資料庫當下的內容不一樣時不通過
	// 採購單不存在、已經審核過、內容被修改過、沒有明細、供應商已停用、規格已永久停產時丟 IllegalArgumentException
	@Transactional
	public void approve(Integer poId, String reviewedContent) {
		PoVO poVO = updatePendingPoStatus(poId, (byte) 1);

		// 狀態已經先改掉，修改採購單（PoService 的 updatePoWithDetails）從這裡開始就進不來，比對的內容不會再變
		if (!getReviewedContent(poVO).equals(reviewedContent)) {
			throw new IllegalArgumentException("採購單內容已被修改，請確認下方最新的內容後再審核");
		}

		List<PoDetailVO> poDetails = poVO.getPoDetails();
		if (poDetails == null || poDetails.isEmpty()) {
			throw new IllegalArgumentException("此採購單沒有採購明細，無法通過");
		}

		VendorVO vendor = poVO.getVendor();
		if (vendor == null || vendor.getIsActive() == null || vendor.getIsActive() != 1) {
			throw new IllegalArgumentException("此採購單的供應商已停用，無法通過，請改為不通過或取消採購");
		}

		// key 是規格編號，TreeMap 會由小到大排；待進貨依這個順序更新，和訂單鎖定規格的順序一致，才不會互相等待
		Map<Integer, Integer> quantityBySkuId = new TreeMap<>();
		for (PoDetailVO poDetailVO : poDetails) {
			ProductSku productSku = poDetailVO.getSkuId();
			if (ProductSku.STATUS_DISCONTINUED.equals(productSku.getStatus())) {
				throw new IllegalArgumentException(
						"商品規格「" + productSku.getDisplayName() + "」已永久停產，無法通過，請改為不通過或取消採購");
			}
			quantityBySkuId.merge(productSku.getSkuId(), poDetailVO.getQuantity(), Integer::sum);
		}

		for (Map.Entry<Integer, Integer> entry : quantityBySkuId.entrySet()) {
			if (poSkuStockRepository.addInboundQty(entry.getKey(), entry.getValue()) != 1) {
				throw new IllegalArgumentException("商品規格編號 " + entry.getKey() + " 不存在，無法更新待進貨");
			}
		}
	}

	// 審核不通過：採購單狀態改為申請未通過（2），不動商品規格
	@Transactional
	public void reject(Integer poId) {
		updatePendingPoStatus(poId, (byte) 2);
	}

	// 取消採購：採購單狀態改為已取消（3），不動商品規格
	@Transactional
	public void cancel(Integer poId) {
		updatePendingPoStatus(poId, (byte) 3);
	}

	// 把待審核的採購單改成新的狀態，回傳改完之後的採購單
	// 先改狀態再查資料：改不到（回傳 0 筆）代表採購單不存在或已經被別人審核過，直接擋下
	private PoVO updatePendingPoStatus(Integer poId, byte newStatus) {
		if (poId == null || poRepository.updateStatusIfPending(poId, newStatus) == 0) {
			throw new IllegalArgumentException("此採購單不存在，或已經審核過，無法再次審核");
		}
		return poRepository.findById(poId)
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));
	}

}
