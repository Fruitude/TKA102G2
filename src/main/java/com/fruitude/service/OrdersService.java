package com.fruitude.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fruitude.entity.Orders;
import com.fruitude.repository.OrdersRepository;

@Service
public class OrdersService {
	@Autowired
	private OrdersRepository ordersRepository;
	
	public List<Orders> findAll() {
		return ordersRepository.findAll();
	}

}
