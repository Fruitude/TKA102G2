package com.fruitude.vendor.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import com.fruitude.vendor.model.VendorService;
import com.fruitude.vendor.model.VendorVO;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Controller
@RequestMapping("/admin/psi/vendor")
public class VendorNoController {

	@Autowired
	VendorService vendorSvc;

	@PostMapping("/getOne_For_Display")
	public String getOne_For_Display(
			@RequestParam(value = "vendorId", required = false) 
			@NotNull(message = "供應商編號，請勿空白") 
			@Min(value = 1, message = "供應商編號: 不能小於{value}") 
			@Max(value = 99999, message = "供應商編號: 不能大於{value}") Integer vendorId,
			ModelMap model) {

		VendorVO vendorVO = vendorSvc.getOneVendor(vendorId);

		String errorMessage = null;
		if (vendorVO == null) {
			errorMessage = "查無資料";
		} else if (vendorVO.getIsActive() == 0) {
			errorMessage = "供應商尚未啟用";
		} else if (vendorVO.getIsActive() == 2) {
			errorMessage = "供應商已停用";
		}
		if (errorMessage != null) {
			model.addAttribute("errorMessage", errorMessage);
			return "admin/psi/vendor/index";
		}

		model.addAttribute("vendorVO", vendorVO);
		return "admin/psi/vendor/listOneVendor"; // 查詢成功，轉交 listOneVendor.html 顯示
	}

	// 依產地模糊查詢已啟用的供應商，結果以 listAllVendor.html 顯示
	@PostMapping("/listVendors_ByOrigin")
	public String listVendors_ByOrigin(Model model, @RequestParam(value = "origin", required = false) String origin) {

		String errorMessage = null;
		List<VendorVO> vendors = null;

		if (origin == null || origin.isBlank()) {
			errorMessage = "產地，請勿空白";
		} else if (!origin.trim().matches("^[\\u4e00-\\u9fa5a-zA-Z]+$")) {
			// 只接受中、英文字母，不可有空白
			errorMessage = "產地: 只能是中、英文字母，不可有空白";
		} else {
			vendors = vendorSvc.getActiveVendorsByOrigin(origin.trim());
			if (vendors.isEmpty()) {
				errorMessage = "查無資料";
			}
		}

		if (errorMessage != null) {
			model.addAttribute("errorMessage", errorMessage);
			return "admin/psi/vendor/index";
		}

		model.addAttribute("vendorListData", vendors);
		return "admin/psi/vendor/listAllVendor";
	}

	// 依聯絡人姓名模糊查詢已啟用的供應商，結果以 listAllVendor.html 顯示
	@PostMapping("/listVendors_ByContactPerson")
	public String listVendors_ByContactPerson(Model model,
			@RequestParam(value = "contactPerson", required = false) String contactPerson) {

		String errorMessage = null;
		List<VendorVO> vendors = null;

		if (contactPerson == null || contactPerson.isBlank()) {
			errorMessage = "聯絡人姓名，請勿空白";
		} else if (!contactPerson.trim().matches("^[\\u4e00-\\u9fa5a-zA-Z]+( [\\u4e00-\\u9fa5a-zA-Z]+)*$")) {
			// 只接受中、英文字母，字元間可以有一個空白
			errorMessage = "聯絡人姓名: 只能是中、英文字母，字元間可以有一個空白";
		} else {
			vendors = vendorSvc.getActiveVendorsByContactPerson(contactPerson.trim());
			if (vendors.isEmpty()) {
				errorMessage = "查無資料";
			}
		}

		if (errorMessage != null) {
			model.addAttribute("errorMessage", errorMessage);
			return "admin/psi/vendor/index";
		}

		model.addAttribute("vendorListData", vendors);
		return "admin/psi/vendor/listAllVendor";
	}
	
	// 依統一編號模糊查詢，結果以 listAllVendor.html 顯示
	@PostMapping("/listVendors_ByTaxId")
	public String listVendors_ByTaxId(Model model, 
			@RequestParam(value = "taxId", required = false) String taxId) {

		String errorMessage = null;
		List<VendorVO> vendors = null;

		if (taxId == null || taxId.isBlank()) {
			errorMessage = "統一編號，請勿空白";
		} else if (!taxId.trim().matches("^\\d+$")) {
			// 只接受數字，不可有空白
			errorMessage = "統一編號，只能輸入數字";
		} else {
			vendors = vendorSvc.getActiveVendorsByTaxId(taxId.trim());
			if (vendors.isEmpty()) {
				errorMessage = "查無資料";
			}
		}

		if (errorMessage != null) {
			model.addAttribute("errorMessage", errorMessage);
			return "admin/psi/vendor/index";
		}

		model.addAttribute("vendorListData", vendors);
		return "admin/psi/vendor/listAllVendor";
	}


	@ExceptionHandler({ HandlerMethodValidationException.class, ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class })
	public ModelAndView handleError(Exception e) {

		List<String> messages = new ArrayList<>();

		if (e instanceof HandlerMethodValidationException ex) {
			// 方法參數驗證失敗（@NotNull、@Min）
			ex.getParameterValidationResults().forEach(
					result -> result.getResolvableErrors().forEach(error -> messages.add(error.getDefaultMessage())));
		} else if (e instanceof ConstraintViolationException ex) {
			// 類別上有加 @Validated 時會丟這個
			ex.getConstraintViolations().forEach(violation -> messages.add(violation.getMessage()));
		} else if (e instanceof MethodArgumentTypeMismatchException) {
			// 轉型失敗（輸入英文字、超過 int 範圍）
			messages.add("供應商編號，只能輸入正整數");
		}

		ModelAndView mav = new ModelAndView("admin/psi/vendor/index");
		mav.addObject("errorMessage", String.join("\n", messages));
		return mav;
	}

}
