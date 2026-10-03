package com.fruitude.po.model;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.podetail.model.PoDetailRepository;
import com.fruitude.podetail.model.PoDetailVO;
import com.fruitude.vendor.model.VendorRepository;

@Service
public class PoService {

	@Autowired
	private PoRepository repository;

	@Autowired
	private PoDetailRepository poDetailRepository;

	@Autowired
	private VendorRepository vendorRepository;
	
	

	public void addPo(PoVO poVO) {
		repository.save(poVO);
	}

	public void updatePo(PoVO poVO) {
		repository.save(poVO);
	}

	public void deletePo(Integer id) {
		if (repository.existsById(id))
			repository.deleteById(id);
	}


	public PoVO getPo(Integer id) {
		Optional<PoVO> optional = repository.findById(id);
		return optional.orElse(null); // public T orElse(T other) : 如果值存在就回傳其值，否則回傳other的值
	}

	public List<PoVO> getAll() {
		return repository.findAll();
	}

}
