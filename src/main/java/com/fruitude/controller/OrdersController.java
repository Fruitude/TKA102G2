package com.fruitude.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fruitude.entity.Orders;
import com.fruitude.service.OrdersService;

@Controller
@RequestMapping("/example/order")
public class OrdersController {
	@Autowired
	private OrdersService ordersService;
	
	@GetMapping({"", "/"})
	public String orderMain() {
		return "example/order_main";
	}
	
	@GetMapping("/getall")
	public String getAll(Model model) {
		List<Orders> ordersList = ordersService.findAll();
		model.addAttribute(ordersList);
		return "example/order";
	}
}
