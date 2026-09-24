package com.fruitude.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.entity.Member;
import com.fruitude.entity.Orders;
import com.fruitude.service.OrdersService;

import jakarta.persistence.Tuple;

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

	@GetMapping("/getalljoin")
	public String getAllWithJoin(Model model) {
		List<Tuple> ordersListJoin = ordersService.findAllWithJoin();
		model.addAttribute("ordersListJoin", ordersListJoin);
		return "example/order_join";
	}

	@GetMapping("/add")
	public String showAddForm(Model model) {
		List<Member> memberList = ordersService.getMemberList();
		model.addAttribute(memberList);
		return "example/add_order";
	}

	/***
	 *  Orders 欄位有十幾個，一個一個寫 @RequestParam 太長，
	 *  改用 @ModelAttribute：表單 input 的 name 跟 Orders 屬性名稱一樣（例如 name="shippingAddress"），
	 *  Spring 會自動把表單值填進 Orders 物件。
	 */
	@PostMapping("/add_order")
	public String insert(@ModelAttribute Orders orders,
			RedirectAttributes redirectAttributes) {
		boolean success = ordersService.insert(orders);
		redirectAttributes.addFlashAttribute("message", success ? "新增成功" : "新增失敗");
		return "redirect:/example/order/add";
	}

	@GetMapping("/delete")
	public String showDelForm() {
		return "example/del_order";
	}

	@PostMapping("/delete")
	public String delete(@RequestParam("ordersId") Integer ordersId,
			RedirectAttributes redirectAttributes) {
		boolean success = ordersService.delete(ordersId);
		redirectAttributes.addFlashAttribute("message", success ? "刪除成功" : "刪除失敗");
		return "redirect:/example/order/delete";
	}

	@GetMapping("/edit")
	public String showEditForm(@RequestParam(value = "ordersId", required = false) Integer ordersId, Model model) {
		List<Member> memberList = ordersService.getMemberList();
		model.addAttribute(memberList);

		if (ordersId != null) {
			model.addAttribute("searched", true);
			model.addAttribute("searchedOrdersId", ordersId);
			Optional<Orders> optional = ordersService.findById(ordersId);
			model.addAttribute("orders", optional.orElse(null));
		}
		return "example/edit_order";
	}

	@PostMapping("/update")
	public String update(
			@RequestParam("ordersId") Integer ordersId,
			@ModelAttribute Orders orders,
			RedirectAttributes redirectAttributes) {

		boolean success = ordersService.updateOrders(ordersId, orders);
		redirectAttributes.addFlashAttribute("message", success ? "更新成功" : "更新失敗");
		return "redirect:/example/order/edit?ordersId=" + ordersId;
	}

	// 做法一：先查出既有的 entity，只改 ordersStatus，再存回去
	@PostMapping("/updateStatusByLoad")
	@ResponseBody
	public String updateStatusByLoad(@RequestParam("ordersId") Integer ordersId, @RequestParam("ordersStatus") Integer ordersStatus) {
		boolean success = ordersService.updateStatusByLoad(ordersId, ordersStatus);
		return success ? "更新成功" : "更新失敗";
	}

	// 做法二：直接下 JPQL UPDATE，只改 ordersStatus
	@PostMapping("/updateStatusByQuery")
	@ResponseBody
	public String updateStatusByQuery(@RequestParam("ordersId") Integer ordersId, @RequestParam("ordersStatus") Integer ordersStatus) {
		boolean success = ordersService.updateStatusByQuery(ordersId, ordersStatus);
		return success ? "更新成功" : "更新失敗";
	}
}
