package com.fruitude.po.model;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.podetail.model.PoDetailRepository;
import com.fruitude.podetail.model.PoDetailVO;
import com.fruitude.product.model.ProductSku;
import com.fruitude.product.model.ProductSkuRepository;
import com.fruitude.vendor.model.VendorRepository;

@Service
public class PoService {

	@Autowired
	private PoRepository repository;

	@Autowired
	private PoDetailRepository poDetailRepository;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private ProductSkuRepository productSkuRepository;
	
	

	public void addPo(PoVO poVO) {
		repository.save(poVO);
	}

	public void updatePo(PoVO poVO) {
		repository.save(poVO);
	}

	// 修改採購明細：從資料庫取出原資料，只覆蓋採購數量與進貨單價，小計與總金額由這裡重算
	// 不直接 save(formPo)，避免表單沒送的欄位被寫成 null、沒送回來的明細被 orphanRemoval 刪除
	@Transactional
	public void updatePoWithDetails(PoVO formPo) {
		PoVO dbPo = repository.findById(formPo.getPoId())
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));

		// 以資料庫的狀態判斷，不採信表單；開著修改頁期間被審核掉的單也會被擋下
		if (!dbPo.isEditable()) {
			throw new IllegalArgumentException("此採購單不是待審核狀態，無法修改");
		}

		for (PoDetailVO formDetail : formPo.getPoDetails()) {
			if (formDetail.getPoDetailId() == null) {
				throw new IllegalArgumentException("採購明細編號遺失");
			}

			PoDetailVO dbDetail = poDetailRepository.findById(formDetail.getPoDetailId())
					.orElseThrow(() -> new IllegalArgumentException("查無此採購明細"));

			// 確認這筆明細真的屬於這張採購單
			if (!dbDetail.getPoId().getPoId().equals(dbPo.getPoId())) {
				throw new IllegalArgumentException("採購明細不屬於此採購單");
			}

			if (formDetail.getQuantity() == null || formDetail.getUnitPrice() == null) {
				throw new IllegalArgumentException("採購數量、進貨單價，請勿空白");
			}

			if (formDetail.getQuantity() < 1) {
				throw new IllegalArgumentException("採購數量必須大於0");
			}

			if (formDetail.getUnitPrice() < 0) {
				throw new IllegalArgumentException("進貨單價不可為負數");
			}

			if (formDetail.getQuantity() < dbDetail.getArrivedPcs()) {
				throw new IllegalArgumentException("採購數量不可小於已到貨數量");
			}

			if (formDetail.getSkuId() == null || formDetail.getSkuId().getSkuId() == null) {
				throw new IllegalArgumentException("商品規格，請勿空白");
			}

			// 規格有更換時才檢查；沒換的明細即使原規格已停產也照原樣保留
			Integer formSkuId = formDetail.getSkuId().getSkuId();
			if (!formSkuId.equals(dbDetail.getSkuId().getSkuId())) {
				ProductSku productSku = productSkuRepository.findById(formSkuId)
						.orElseThrow(() -> new IllegalArgumentException("查無此商品規格"));

				// 下拉選單只列這張單供應商的規格，這裡擋直接送出其他編號的情況
				if (productSku.getProduct() == null || productSku.getProduct().getVendor() == null
						|| !productSku.getProduct().getVendor().getVendorId().equals(dbPo.getVendor().getVendorId())) {
					throw new IllegalArgumentException("商品規格不屬於此採購單的供應商");
				}

				if (ProductSku.STATUS_DISCONTINUED.equals(productSku.getStatus())) {
					throw new IllegalArgumentException("商品規格已永久停產，無法採購");
				}

				dbDetail.setSkuId(productSku);
			}

			dbDetail.setQuantity(formDetail.getQuantity());
			dbDetail.setUnitPrice(formDetail.getUnitPrice());
			dbDetail.setSubtotal(formDetail.getQuantity() * formDetail.getUnitPrice());
		}

		// 同一張採購單不可有重複的商品規格（資料表有 po_id + sku_id 的唯一限制）
		Set<Integer> skuIds = new HashSet<>();
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			if (!skuIds.add(dbDetail.getSkuId().getSkuId())) {
				throw new IllegalArgumentException("同一張採購單不可有重複的商品規格");
			}
		}

		// 總金額不收表單的值，以這張採購單所有明細的小計加總
		int totalAmount = 0;
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			totalAmount += dbDetail.getSubtotal();
		}
		dbPo.setTotalAmount(totalAmount);
	}

	public void deletePo(Integer PoId) {
		if (repository.existsById(PoId))
			repository.deleteById(PoId);
	}


	public PoVO getOnePo(Integer PoId) {
		Optional<PoVO> optional = repository.findById(PoId);
		return optional.orElse(null); // public T orElse(T other) : 如果值存在就回傳其值，否則回傳other的值
	}

	public List<PoVO> getAll() {
		return repository.findAll();
	}
	
	public List<PoVO> getByPoStatus(Byte poStatus){
		return repository.getByPoStatus(poStatus);
	}

}
