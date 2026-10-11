package com.fruitude.product.model;

import java.util.*;
import java.math.BigDecimal;
import com.fruitude.po.model.*;
import com.fruitude.podetail.model.*;
import com.fruitude.vendor.model.VendorVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Yield-aware purchase suggestions; saving, pricing and approval remain owned by the PO module. */
@Service
@Transactional(readOnly=true)
public class ProductProcurementService {
    private final ProductSkuRepository skus;
    private final SkuSupplyService supply;
    private final PoService poService;
    private final PoRepository repository;
    private final PoDetailRepository poDetailRepository;
    public ProductProcurementService(ProductSkuRepository skus, SkuSupplyService supply, PoService poService,
            PoRepository repository, PoDetailRepository poDetailRepository) {
        this.skus=skus; this.supply=supply; this.poService=poService;
        this.repository=repository; this.poDetailRepository=poDetailRepository;
    }
    public List<ProductSku> getBelowSafetyStock() {
        var candidates=skus.findSupplyMonitoringCandidates();
        var rates=supply.yields(candidates.stream().map(ProductSku::getSkuId).toList());
        candidates.forEach(s->s.setPurchaseYield(rates.getOrDefault(s.getSkuId(),SkuSupplyService.DEFAULT_YIELD)));
        return candidates.stream().filter(s->SkuSupplyService.needsPurchase(s,s.getPurchaseYield())).toList();
    }
    public Map<Integer,Integer> getSuggestedQuantityBySkuId(Integer vendorId,Integer poId) {
        var own=new HashMap<Integer,Long>();
        var po=poId==null?null:repository.findById(poId).orElse(null);
        if(po!=null && po.isEditable()) po.getPoDetails().forEach(d->own.merge(d.getSkuId().getSkuId(),d.getQuantity().longValue(),Long::sum));
        var pending=poService.getPendingQuantityBySkuId();
        var result=new HashMap<Integer,Integer>();
        for(var sku:getBelowSafetyStockByVendor().getOrDefault(vendorId,List.of())) {
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
	public Map<Integer, List<ProductSku>> getBelowSafetyStockByVendor() {
		Map<Integer, List<ProductSku>> skusByVendorId = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
		for (ProductSku productSku : getBelowSafetyStock()) {
			VendorVO vendor = productSku.getProduct().getVendor();
			Integer vendorId = vendor == null ? null : vendor.getVendorId();
			skusByVendorId.computeIfAbsent(vendorId, key -> new ArrayList<>()).add(productSku);
		}
		return skusByVendorId;
	}

	public List<PoDetailVO> getShortageDetailsByVendorId(Integer vendorId) {
		Map<Integer, Integer> suggestedQuantityBySkuId = getSuggestedQuantityBySkuId(vendorId, null);
		List<PoDetailVO> poDetails = new ArrayList<>();
		for (ProductSku productSku : getBelowSafetyStockByVendor().getOrDefault(vendorId, List.of())) {
			if (!suggestedQuantityBySkuId.containsKey(productSku.getSkuId())) {
				continue;
			}
			PoDetailVO poDetailVO = new PoDetailVO();
			poDetailVO.setSkuId(productSku);
			poDetails.add(poDetailVO);
		}
		return poDetails;
	}

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

	public PoVO getPoWithShortageDetails(Integer poId) {
		PoVO dbPo = repository.findById(poId).orElse(null);
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
			Map<Integer, Integer> suggestedQuantityBySkuId = getSuggestedQuantityBySkuId(vendorId, poId);
			for (ProductSku productSku : getBelowSafetyStockByVendor().getOrDefault(vendorId, List.of())) {
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
