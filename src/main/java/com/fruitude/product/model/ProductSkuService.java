package com.fruitude.product.model;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductSkuService {

	@Autowired
	ProductSkuRepository repository;

	public void addProductSku(ProductSku productSku) {
		repository.save(productSku);
	}

	public void updateProductSku(ProductSku productSku) {
		repository.save(productSku);
	}

	public void deleteProductSku(Integer skuId) {
		if (repository.existsById(skuId)) {
			repository.deleteById(skuId);
		}
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
}