package com.fruitude.vendor.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VendorService {

	@Autowired
	private VendorRepository repository;

	public void addVendor(VendorVO vendorVO) {
		repository.save(vendorVO);
	}

	public void updateVendor(VendorVO vendorVO) {
		if (vendorVO.getLogo() == null) {
			repository.findById(vendorVO.getVendorId()).ifPresent(old -> vendorVO.setLogo(old.getLogo()));
		}
		repository.save(vendorVO);
	}

	public VendorVO getOneVendor(Integer vendorId) {
		Optional<VendorVO> optional = repository.findById(vendorId);
		return optional.orElse(null); // public T orElse(T other) : 如果值存在就回傳其值，否則回傳other的值
	}

	public List<VendorVO> getAll() {
		return repository.findAll();
	}

	public boolean existsByTaxId(String taxId) {
		return repository.existsByTaxId(taxId);
	}

	public boolean existsByTaxIdAndVendorIdNot(String taxId, Integer vendorId) {
		return repository.existsByTaxIdAndVendorIdNot(taxId, vendorId);
	}

	public List<VendorVO> getActiveVendors() {
		return getByIsActive((byte) 1);
	}

	public List<VendorVO> getByIsActive(Byte isActive) {
		return repository.findByIsActive(isActive);
	}

	// 依產地模糊查詢，只查已啟用的供應商
	public List<VendorVO> getActiveVendorsByOrigin(String origin) {
		return repository.findByOriginContainingAndIsActive(origin, (byte) 1);
	}

	// 依聯絡人姓名模糊查詢，只查已啟用的供應商
	public List<VendorVO> getActiveVendorsByContactPerson(String contactPerson) {
		return repository.findByContactPersonContainingAndIsActive(contactPerson, (byte) 1);
	}
	
	public List<VendorVO> getActiveVendorsByTaxId(String taxId) {
		return repository.findByTaxIdContainingAndIsActive(taxId, (byte) 1);
	}
	
	// 已啟用供應商的編號與名稱，給下拉選單使用；不會讀出 logo
		public List<Map<String, Object>> getActiveVendorOptions() {
		    return repository.findOptionsByIsActive((byte) 1);
		}
}
