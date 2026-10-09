package com.fruitude.product.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fruitude.vendor.model.VendorVO;

@Service
public class ProductSkuService {

	@Autowired
	ProductSkuRepository repository;

    @Autowired ProductRepository products;
    @Autowired ProductLifecycleService lifecycle;

    @org.springframework.transaction.annotation.Transactional
    public void addProductSku(ProductSku sku) {
        Product product = products.lockForStatus(sku.getProduct().getProductId()).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        lifecycle.validateSkuChange(null, sku.getStatus());
        sku.setProduct(product); product.getProductSkus().add(sku);
        lifecycle.synchronize(product);
        products.saveAndFlush(product);
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateProductSku(ProductSku form) {
        ProductSku reference = repository.findById(form.getSkuId()).orElseThrow(() -> new IllegalArgumentException("規格不存在"));
        Product product = products.lockForStatus(reference.getProduct().getProductId()).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        ProductSku sku = product.getProductSkus().stream().filter(s -> s.getSkuId().equals(form.getSkuId())).findFirst().orElseThrow();
        lifecycle.validateSkuChange(sku.getStatus(), form.getStatus());
        sku.setSkuName(form.getSkuName().trim()); sku.setAnotherName(form.getAnotherName());
        sku.setPrice(form.getPrice()); sku.setStock(form.getStock()); sku.setSafetyStock(form.getSafetyStock());
        sku.setInboundQty(form.getInboundQty()); sku.setOutboundQty(form.getOutboundQty());
        sku.setStatus(form.getStatus()); sku.setUpdatedAt(java.time.LocalDateTime.now());
        lifecycle.synchronize(product);
        products.saveAndFlush(product);
    }

    public record StatusResult(Integer productId, Byte productStatus, java.util.Map<Integer, Byte> skuStatuses) {}

    @org.springframework.transaction.annotation.Transactional
    public StatusResult updateStatus(Integer skuId, Byte status) {
        ProductSku reference = repository.findById(skuId).orElseThrow(() -> new java.util.NoSuchElementException("規格不存在"));
        Product product = products.lockForStatus(reference.getProduct().getProductId())
            .orElseThrow(() -> new java.util.NoSuchElementException("商品不存在"));
        ProductSku sku = product.getProductSkus().stream().filter(s -> s.getSkuId().equals(skuId))
            .findFirst().orElseThrow(() -> new java.util.NoSuchElementException("規格不存在"));
        lifecycle.validateSkuChange(sku.getStatus(), status);
        sku.setStatus(status); sku.setUpdatedAt(java.time.LocalDateTime.now());
        lifecycle.synchronize(product);
        products.saveAndFlush(product);
        var states = new java.util.LinkedHashMap<Integer, Byte>();
        product.getProductSkus().forEach(s -> states.put(s.getSkuId(), s.getStatus()));
        return new StatusResult(product.getProductId(), product.getStatus(), states);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteProductSku(Integer skuId) {
        ProductSku reference = repository.findById(skuId).orElse(null);
        if (reference == null) return;
        Product product = products.lockForStatus(reference.getProduct().getProductId()).orElseThrow();
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

	// 某供應商可採購的規格：status 0~3 都列入，4（永久停產）不列入
	public List<ProductSku> getPurchasableByVendorId(Integer vendorId) {
		return repository.findByProduct_Vendor_VendorIdAndStatusNotOrderBySkuIdAsc(vendorId,
				ProductSku.STATUS_DISCONTINUED);
	}

	// 新增時檢查同商品是否已有相同規格名稱
	public boolean isSkuNameExist(Integer productId, String skuName) {
		return repository.existsByProduct_ProductIdAndSkuName(productId, skuName);
	}

	// 修改時檢查重複名稱，但排除目前這筆規格
	public boolean isSkuNameExistExcludeSkuId(Integer productId, String skuName, Integer skuId) {

		return repository.existsByProduct_ProductIdAndSkuNameAndSkuIdNot(productId, skuName, skuId);
	}
	
	// 需要採購的規格：上架（1）或缺貨（2），且低於安全庫存
	public List<ProductSku> getBelowSafetyStock() {
	        return repository.findBelowSafetyStockByStatusIn(List.of((byte) 1, (byte) 2));
	}

	// 需要採購的規格依供應商分組：key 為供應商編號（由小到大），沒有供應商的排最後
	public Map<Integer, List<ProductSku>> getBelowSafetyStockByVendor() {
		Map<Integer, List<ProductSku>> skusByVendorId = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
		for (ProductSku productSku : getBelowSafetyStock()) {
			VendorVO vendor = productSku.getProduct().getVendor();
			Integer vendorId = vendor == null ? null : vendor.getVendorId();
			skusByVendorId.computeIfAbsent(vendorId, key -> new ArrayList<>()).add(productSku);
		}
		return skusByVendorId;
	}
}