package com.fruitude.vendor.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
@RequestMapping("/admin/psi/vendor/")
public class VendorNoController {

	@Autowired
	VendorService vendorSvc;

	@PostMapping("getOne_For_Display")
	public String getOne_For_Display(
	        @RequestParam(value = "vendorId", required = false)
	        @NotNull(message = "供應商編號，請勿空白")
	        @Min(value = 1, message = "供應商編號: 不能小於{value}")
	        @Max(value = 99999, message = "供應商編號: 不能大於於{value}")
	        Integer vendorId,
	        ModelMap model) {

	    VendorVO vendorVO = vendorSvc.getOneVendor(vendorId);   // 不再需要 Integer.valueOf
	    model.addAttribute("vendorListData", vendorSvc.getAll());

	    if (vendorVO == null) {
	        model.addAttribute("errorMessage", "查無資料");
	        return "admin/psi/vendor/index";
	    }

	    model.addAttribute("vendorVO", vendorVO);
	    return "admin/psi/vendor/index"; // 回到 index，由 index include listOneVendor.html 的 vendorOneTable
	}

	@ExceptionHandler({ HandlerMethodValidationException.class, ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class })
	public ModelAndView handleError(Exception e) {

		StringBuilder strBuilder = new StringBuilder();

		if (e instanceof HandlerMethodValidationException ex) {
			// 方法參數驗證失敗（@NotNull、@Min）
			ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors()
					.forEach(error -> strBuilder.append(error.getDefaultMessage()).append("<br>")));
		} else if (e instanceof ConstraintViolationException ex) {
			// 類別上有加 @Validated 時會丟這個
			ex.getConstraintViolations().forEach(violation -> strBuilder.append(violation.getMessage()).append("<br>"));
		} else if (e instanceof MethodArgumentTypeMismatchException) {
			// 轉型失敗（輸入英文字、超過 int 範圍）
			strBuilder.append("供應商編號，只能輸入正整數<br>");
		}

		ModelAndView mav = new ModelAndView("admin/psi/vendor/index");
		mav.addObject("errorMessage", strBuilder.toString());
		mav.addObject("vendorListData", vendorSvc.getAll());
		return mav;
	}

}
