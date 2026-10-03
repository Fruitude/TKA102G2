package com.fruitude.podetail.model;

import java.util.List;
import java.util.Optional;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class PoDetailService {
	
	
	@Autowired
	private PoDetailRepository repository;
	
	public void addPoDetail(PoDetailVO poDetailVO) {
		repository.save(poDetailVO);
	}
	
	public void updatePoDetail(PoDetailVO poDetailVO) {
		repository.save(poDetailVO);
	}
	
	
	public PoDetailVO getPoDetail(Integer poDetailId) {
		Optional<PoDetailVO> optional = repository.findById(poDetailId);
//		return optional.get();
		return optional.orElse(null);  // public T orElse(T other) : 如果值存在就回傳其值，否則回傳other的值
	}
	
	private void calcSubtotal(PoDetailVO d) {
	    d.setSubtotal(d.getQuantity() * d.getUnitPrice());
	}
	
	public List<PoDetailVO> getAll() {
		return repository.findAll();
	}

}
