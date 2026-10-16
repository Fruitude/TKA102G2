package com.fruitude.product.model;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.math.BigDecimal;
import com.fruitude.po.model.*;
import com.fruitude.podetail.model.*;
import com.fruitude.vendor.model.VendorVO;

@Service
public class ProductSkuService {
    public ProductSkuService() {}

    @org.springframework.beans.factory.annotation.Autowired
    private ProductInventoryLockService inventoryLocks;
    private java.util.Optional<Product> lockProduct(Integer id) {
        return inventoryLocks==null ? products.lockForStatus(id) : inventoryLocks.lockProduct(id);
    }


	@Autowired
	ProductSkuRepository repository;

    @Autowired ProductRepository products;
    @Autowired ProductLifecycleService lifecycle;

    @org.springframework.transaction.annotation.Transactional
    public void addProductSku(ProductSku sku) {
        Product product = lockProduct(sku.getProduct().getProductId()).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        lifecycle.validateSkuChange(null, sku.getStatus());
        sku.setProduct(product); product.getProductSkus().add(sku);
        lifecycle.synchronize(product);
        products.saveAndFlush(product);
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateProductSku(ProductSku form) { updateProductSku(form, false); }

    @org.springframework.transaction.annotation.Transactional
    public void updateProductSku(ProductSku form, boolean restockConfirmed) {
        ProductSku reference = repository.findById(form.getSkuId()).orElseThrow(() -> new IllegalArgumentException("規格不存在"));
        Product product = lockProduct(reference.getProduct().getProductId()).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        ProductSku sku = product.getProductSkus().stream().filter(s -> s.getSkuId().equals(form.getSkuId())).findFirst().orElseThrow();
        lifecycle.validateSkuChange(sku.getStatus(), form.getStatus(), restockConfirmed);
        sku.setSkuName(form.getSkuName().trim()); sku.setAnotherName(form.getAnotherName());
        sku.setPrice(form.getPrice()); sku.setStock(form.getStock()); sku.setSafetyStock(form.getSafetyStock());
        sku.setInboundQty(form.getInboundQty()); sku.setOutboundQty(form.getOutboundQty());
        if (form.getMaxBackorderQty() != null && form.getMaxBackorderQty() >= 0) sku.setMaxBackorderQty(form.getMaxBackorderQty());
        sku.setStatus(form.getStatus()); sku.setUpdatedAt(java.time.LocalDateTime.now());
        lifecycle.synchronize(product);
        products.saveAndFlush(product);
    }

    public record StatusResult(Integer productId, Byte productStatus, java.util.Map<Integer, Byte> skuStatuses, String message) {}

    @org.springframework.transaction.annotation.Transactional
    public StatusResult updateStatus(Integer skuId, Byte status) { return updateStatus(skuId, status, false); }

    @org.springframework.transaction.annotation.Transactional
    public StatusResult updateStatus(Integer skuId, Byte status, boolean restockConfirmed) {
        ProductSku reference = repository.findById(skuId).orElseThrow(() -> new java.util.NoSuchElementException("規格不存在"));
        Product product = lockProduct(reference.getProduct().getProductId())
            .orElseThrow(() -> new java.util.NoSuchElementException("商品不存在"));
        ProductSku sku = product.getProductSkus().stream().filter(s -> s.getSkuId().equals(skuId))
            .findFirst().orElseThrow(() -> new java.util.NoSuchElementException("規格不存在"));
        lifecycle.validateSkuChange(sku.getStatus(), status, restockConfirmed);
        sku.setStatus(status); sku.setUpdatedAt(java.time.LocalDateTime.now());
        lifecycle.synchronize(product);
        products.saveAndFlush(product);
        var states = new java.util.LinkedHashMap<Integer, Byte>();
        product.getProductSkus().forEach(s -> states.put(s.getSkuId(), s.getStatus()));
        String message=Byte.valueOf((byte)1).equals(status) && Byte.valueOf((byte)2).equals(sku.getStatus())
            ? "已設定上架，但預估供應量低於安全庫存，系統將依供應狀況調整為缺貨。" : null;
        return new StatusResult(product.getProductId(), product.getStatus(), states,message);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteProductSku(Integer skuId) {
        ProductSku reference = repository.findById(skuId).orElse(null);
        if (reference == null) return;
        Product product = lockProduct(reference.getProduct().getProductId()).orElseThrow();
        product.getProductSkus().removeIf(s -> s.getSkuId().equals(skuId));
        lifecycle.synchronize(product); products.saveAndFlush(product);
    }

	public ProductSku getOneProductSku(Integer skuId) {
		Optional<ProductSku> optional = repository.findById(skuId);

		return optional.orElse(null);
	}

	public List<ProductSku> getAll() {
		return repository.findAll();
	}

	// 新增時檢查同商品是否已有相同規格名稱
	public boolean isSkuNameExist(Integer productId, String skuName) {
		return repository.existsByProduct_ProductIdAndSkuName(productId, skuName);
	}

	// 修改時檢查重複名稱，但排除目前這筆規格
	public boolean isSkuNameExistExcludeSkuId(Integer productId, String skuName, Integer skuId) {

		return repository.existsByProduct_ProductIdAndSkuNameAndSkuIdNot(productId, skuName, skuId);
	}

    // Yield-aware procurement suggestions
    public ProductSkuService(ProductSkuRepository skus, SkuSupplyService supply, PoService poService,
            PoRepository repository, PoDetailRepository details) {
        this.repository=skus;this.supply=supply;this.poService=poService;this.poRepository=repository;this.poDetailRepository=details;
    }


    @Autowired private SkuSupplyService supply;
    @Autowired private PoService poService;
    @Autowired private PoRepository poRepository;
    @Autowired private PoDetailRepository poDetailRepository;

    @Transactional(readOnly=true)
    public List<ProductSku> getBelowSafetyStock() {
        var candidates=repository.findSupplyMonitoringCandidates();
        var rates=supply.yields(candidates.stream().map(ProductSku::getSkuId).toList());
        candidates.forEach(s->s.setPurchaseYield(rates.getOrDefault(s.getSkuId(),SkuSupplyService.DEFAULT_YIELD)));
        return candidates.stream().filter(s->SkuSupplyService.needsPurchase(s,s.getPurchaseYield())).toList();
    }
    @Transactional(readOnly=true)
    public Map<Integer,Integer> getSuggestedQuantityBySkuId(Integer vendorId,Integer poId) {
        var skus=getBelowSafetyStockByVendor().getOrDefault(vendorId,List.of());
        var po=poId==null?null:poRepository.findById(poId).orElse(null);
        return suggestedQuantities(skus,po);
    }
    // Reuse one shortage/yield snapshot within each operation; exclude the edited PO's own pending quantity.
    private Map<Integer,Integer> suggestedQuantities(List<ProductSku> skus,PoVO po) {
        var own=new HashMap<Integer,Long>();
        if(po!=null && po.isEditable()) po.getPoDetails().forEach(d->own.merge(d.getSkuId().getSkuId(),d.getQuantity().longValue(),Long::sum));
        var pending=poService.getPendingQuantityBySkuId();
        var result=new HashMap<Integer,Integer>();
        for(var sku:skus) {
            long other=Math.max(0,pending.getOrDefault(sku.getSkuId(),0L)-own.getOrDefault(sku.getSkuId(),0L));
            Integer qty=suggestedQuantity(sku,sku.getPurchaseYield(),other);
            if(qty!=null && qty>0) result.put(sku.getSkuId(),qty);
        }
        return result;
    }
    public static Integer suggestedQuantity(ProductSku sku,BigDecimal yield,long otherPending) {
        Integer required=SkuSupplyService.suggestedPurchase(sku,yield);
        if(required==null) return null; // Zero yield requires supplier review, not division by zero.
        return (int)Math.min(9999,Math.max(0,(long)required-Math.max(0,otherPending)));
    }
    @Transactional(readOnly=true)
	public Map<Integer, List<ProductSku>> getBelowSafetyStockByVendor() {
		Map<Integer, List<ProductSku>> skusByVendorId = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
		for (ProductSku productSku : getBelowSafetyStock()) {
			VendorVO vendor = productSku.getProduct().getVendor();
			Integer vendorId = vendor == null ? null : vendor.getVendorId();
			skusByVendorId.computeIfAbsent(vendorId, key -> new ArrayList<>()).add(productSku);
		}
		return skusByVendorId;
	}

    @Transactional(readOnly=true)
	public List<PoDetailVO> getShortageDetailsByVendorId(Integer vendorId) {
		var skus = getBelowSafetyStockByVendor().getOrDefault(vendorId, List.of());
		Map<Integer, Integer> suggestedQuantityBySkuId = suggestedQuantities(skus, null);
		List<PoDetailVO> poDetails = new ArrayList<>();
		for (ProductSku productSku : skus) {
			if (!suggestedQuantityBySkuId.containsKey(productSku.getSkuId())) {
				continue;
			}
			PoDetailVO poDetailVO = new PoDetailVO();
			poDetailVO.setSkuId(productSku);
			poDetails.add(poDetailVO);
		}
		return poDetails;
	}

    @Transactional(readOnly=true)
	public Map<Integer, Integer> getPendingPoIdByVendorId() {
		Map<Integer, Integer> pendingPoIdBySkuId = new HashMap<>();
		for (Object[] row : poDetailRepository.findLatestPendingPoIdBySkuId()) {
			pendingPoIdBySkuId.put((Integer) row[0], (Integer) row[1]);
		}

		Map<Integer, Integer> pendingPoIdByVendorId = new HashMap<>();
		for (Map.Entry<Integer, List<ProductSku>> vendorEntry : getBelowSafetyStockByVendor().entrySet()) {
			if (vendorEntry.getKey() == null) {
				continue;
			}
			for (ProductSku productSku : vendorEntry.getValue()) {
				Integer pendingPoId = pendingPoIdBySkuId.get(productSku.getSkuId());
				if (pendingPoId != null) {
					pendingPoIdByVendorId.merge(vendorEntry.getKey(), pendingPoId, Math::max);
				}
			}
		}
		return pendingPoIdByVendorId;
	}

    @Transactional(readOnly=true)
	public PoVO getPoWithShortageDetails(Integer poId) {
		PoVO dbPo = poRepository.findById(poId).orElse(null);
		if (dbPo == null) {
			return null;
		}

		PoVO formPo = new PoVO();
		formPo.setPoId(dbPo.getPoId());
		formPo.setPoNo(dbPo.getPoNo());
		formPo.setVendor(dbPo.getVendor());
		formPo.setPoEmployeeId(dbPo.getPoEmployeeId());
		formPo.setOrderDate(dbPo.getOrderDate());
		formPo.setPoStatus(dbPo.getPoStatus());
		formPo.setTotalAmount(dbPo.getTotalAmount());

		List<PoDetailVO> formDetails = new ArrayList<>();
		Set<Integer> skuIdsInPo = new HashSet<>();
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			PoDetailVO formDetail = new PoDetailVO();
			formDetail.setPoDetailId(dbDetail.getPoDetailId());
			formDetail.setPoId(formPo);
			formDetail.setSkuId(dbDetail.getSkuId());
			formDetail.setQuantity(dbDetail.getQuantity());
			formDetail.setUnitPrice(dbDetail.getUnitPrice());
			formDetail.setSubtotal(dbDetail.getSubtotal());
			formDetails.add(formDetail);
			skuIdsInPo.add(dbDetail.getSkuId().getSkuId());
		}

		if (dbPo.isEditable()) {
			Integer vendorId = dbPo.getVendor().getVendorId();
			var skus = getBelowSafetyStockByVendor().getOrDefault(vendorId, List.of());
			Map<Integer, Integer> suggestedQuantityBySkuId = suggestedQuantities(skus, dbPo);
			for (ProductSku productSku : skus) {
				if (skuIdsInPo.contains(productSku.getSkuId())
						|| !suggestedQuantityBySkuId.containsKey(productSku.getSkuId())) {
					continue;
				}
				PoDetailVO formDetail = new PoDetailVO();
				formDetail.setPoId(formPo);
				formDetail.setSkuId(productSku);
				formDetail.setSubtotal(0);
				formDetails.add(formDetail);
			}
		}

		formPo.setPoDetails(formDetails);
		return formPo;
	}

}
