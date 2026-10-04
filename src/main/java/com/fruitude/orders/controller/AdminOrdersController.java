package com.fruitude.orders.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fruitude.orders.model.OrdersService;

import jakarta.persistence.Tuple;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrdersController {
	@Autowired
	private OrdersService ordersService;
	
	@GetMapping("/")
	public String getOrderList(Model model) {
		List<Tuple> list = ordersService.findAllWithJoin();
		model.addAttribute("ordersList", list);
		return "admin/orders/index";
	}
	
	@GetMapping("/orderDetail")
	public String getOrderDetail(@RequestParam Integer ordersId, Model model) {
		List<Tuple> dataList = ordersService.getOrderDetailById(ordersId);
		model.addAttribute("ordersDetailList", dataList);
		return "admin/orders/detail/index";
	}
}
