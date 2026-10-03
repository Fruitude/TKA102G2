package com.fruitude.vendor.model;

import java.util.List;
import java.util.Optional;

import org.hibernate.SessionFactory;
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
	        repository.findById(vendorVO.getVendorId())
	                  .ifPresent(old -> vendorVO.setLogo(old.getLogo()));
	    }
	    repository.save(vendorVO);
	}
	
	public void deleteVendor(Integer vendorId) {
		repository.deleteById(vendorId);
	}
	
	public VendorVO getOneVendor(Integer vendorId) {
		Optional<VendorVO> optional = repository.findById(vendorId);
//		return optional.get();
		return optional.orElse(null);  // public T orElse(T other) : 如果值存在就回傳其值，否則回傳other的值
	}
	
	public List<VendorVO> getAll() {
		return repository.findAll();
	}
	

}
