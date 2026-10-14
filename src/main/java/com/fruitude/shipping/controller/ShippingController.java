package com.fruitude.shipping.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.employee.model.Employee;
import com.fruitude.orders.model.Orders;
import com.fruitude.shipping.model.ShippingDetail;
import com.fruitude.shipping.model.ShippingService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin/psi/shipping")
public class ShippingController {

	@Autowired
	ShippingService shippingSvc;

	// 出貨系統首頁：列出待出貨的訂單，每頁 10 筆；page 是頁碼，從 1 開始，網址沒帶就是第 1 頁
	@GetMapping("")
	public String shipping(@RequestParam(value = "page", defaultValue = "1") int page, Model model) {
		System.out.println("執行shipping導向");

		model.addAttribute("ordersPage", shippingSvc.getPendingOrders(page));
		return "admin/psi/shipping/index"; //view
	}

	// 單筆資料：由首頁列表的「詳細」連過來，依 ordersId 顯示訂單與出貨明細
	// 只顯示待出貨（ordersStatus = 0）的訂單
	@GetMapping("/listOneShipping")
	public String listOneShipping(@RequestParam("ordersId") Integer ordersId, Model model,
			RedirectAttributes redirectAttributes, HttpSession session) {
		Orders orders = shippingSvc.getOneOrders(ordersId);

		// 查不到訂單、或不是待出貨的訂單時回首頁顯示錯誤訊息；ship 被擋下帶過來的錯誤訊息繼續帶到首頁顯示，沒有才用預設的訊息
		if (orders == null || !Integer.valueOf(0).equals(orders.getOrdersStatus())) {
			Object errorMessage = model.containsAttribute("errorMessage") ? model.getAttribute("errorMessage")
					: "查無此訂單，或訂單已經不是待出貨";
			redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
			return "redirect:/admin/psi/shipping";
		}
		List<ShippingDetail> shippingDetails = shippingSvc.getShippingDetails(ordersId);
		model.addAttribute("orders", orders);
		model.addAttribute("shippingDetails", shippingDetails);
		// 「確認出貨」能不能按：有明細而且庫存都夠
		model.addAttribute("shippable", shippingSvc.isShippable(shippingDetails));
		// 出貨人員：目前登入的員工，按下「確認出貨」時會記錄這位員工
		model.addAttribute("shippingEmployee", getLoggedInEmployee(session));

		return "admin/psi/shipping/listOneShipping"; //view
	}

	// 確認出貨：由單筆頁的「確認出貨」按鈕送過來，出貨員工取自 session 的登入員工
	// 成功時 redirect 到出貨紀錄的單筆頁（updateShipping）顯示「出貨完成」；被擋下時 redirect 回待出貨的單筆頁顯示原因
	@PostMapping("/ship")
	public String ship(@RequestParam("ordersId") Integer ordersId, RedirectAttributes redirectAttributes,
			HttpSession session) {
		// 沒登入、登入逾時或查無此員工時是 null，shipOrders 會擋下
		Employee shippingEmployee = getLoggedInEmployee(session);
		try {
			shippingSvc.shipOrders(ordersId, shippingEmployee == null ? null : shippingEmployee.getEmployeeId());
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/psi/shipping/listOneShipping?ordersId=" + ordersId;
		}
		// updateShipping.html 看到 shipped，返回按鈕會改成回出貨管理；success 是綠色提示的文字
		redirectAttributes.addFlashAttribute("shipped", true);
		redirectAttributes.addFlashAttribute("success", "出貨完成");
		return "redirect:/admin/psi/shipping/updateShipping?ordersId=" + ordersId;
	}

	// 歷史出貨紀錄：由首頁的「查看歷史出貨紀錄」連過來，列出狀態不是待出貨、也不是取消的訂單，每頁 10 筆
	@GetMapping("/listAllShipping")
	public String listAllShipping(@RequestParam(value = "page", defaultValue = "1") int page, Model model) {
		model.addAttribute("ordersPage", shippingSvc.getHistoryOrders(page));
		return "admin/psi/shipping/listAllShipping"; //view
	}

	// 取消出貨：由出貨紀錄單筆頁（updateShipping）的「取消出貨」按鈕送過來
	// 成功時 redirect 回同一頁顯示「已取消出貨」；被擋下時 redirect 回出貨管理首頁顯示原因
	@PostMapping("/cancelShip")
	public String cancelShip(@RequestParam("ordersId") Integer ordersId, RedirectAttributes redirectAttributes) {
		try {
			shippingSvc.cancelShipOrders(ordersId);
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/psi/shipping";
		}
		// updateShipping 看到 cancelled 才會放行這張已經改回待出貨的訂單；success 是綠色提示的文字
		redirectAttributes.addFlashAttribute("cancelled", true);
		redirectAttributes.addFlashAttribute("success", "已取消出貨");
		return "redirect:/admin/psi/shipping/updateShipping?ordersId=" + ordersId;
	}

	// 歷史出貨紀錄的單筆資料：由歷史出貨紀錄的「詳細」連過來，或剛出貨完由 ship redirect 過來，依 ordersId 顯示訂單與出貨明細
	// 剛取消出貨由 cancelShip redirect 過來的那一次，訂單已經是待出貨也照樣顯示
	@GetMapping("/updateShipping")
	public String updateShipping(@RequestParam("ordersId") Integer ordersId, Model model,
			RedirectAttributes redirectAttributes) {
		Orders orders = shippingSvc.getOneOrders(ordersId);

		// 剛取消出貨的那一次才有 cancelled；重新整理後就沒有，待出貨的訂單會照下面的規則回首頁
		boolean justCancelled = orders != null && model.containsAttribute("cancelled")
				&& Integer.valueOf(0).equals(orders.getOrdersStatus());

		// 查不到訂單、或不是歷史出貨紀錄會列出的訂單（待出貨、取消）時回首頁顯示錯誤訊息
		if (orders == null || (!shippingSvc.isHistoryOrders(orders) && !justCancelled)) {
			redirectAttributes.addFlashAttribute("errorMessage", "查無此訂單，或訂單不在歷史出貨紀錄裡");
			return "redirect:/admin/psi/shipping";
		}
		model.addAttribute("orders", orders);
		model.addAttribute("shippingDetails", shippingSvc.getShippingDetails(ordersId));
		// 出貨人員：資料庫裡記錄的出貨員工；舊資料沒記錄或剛取消出貨時是 null
		model.addAttribute("shippingEmployee", shippingSvc.getOneEmployee(orders.getEmployeeId()));

		return "admin/psi/shipping/updateShipping"; //view
	}

	// 目前登入後台的員工（EmployeeSessionController 登入時放進 session 的 loggedInEmployeeId）
	// 沒登入、登入逾時或查無此員工時回傳 null
	private Employee getLoggedInEmployee(HttpSession session) {
		Object employeeId = session.getAttribute("loggedInEmployeeId");
		return employeeId instanceof Number ? shippingSvc.getOneEmployee(((Number) employeeId).intValue()) : null;
	}

	// 網址的參數型別不對（例如 page=abc）或少了必要的參數時，回首頁第 1 頁並顯示錯誤訊息
	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
	public ModelAndView handleError(Exception e) {
		ModelAndView mav = new ModelAndView("admin/psi/shipping/index");
		mav.addObject("errorMessage", "網址的參數不正確，請從出貨系統管理重新操作");
		mav.addObject("ordersPage", shippingSvc.getPendingOrders(1));
		return mav;
	}

}
