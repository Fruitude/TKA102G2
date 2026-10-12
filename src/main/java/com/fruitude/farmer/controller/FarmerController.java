package com.fruitude.farmer.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.farmer.model.FarmerForm;
import com.fruitude.farmer.model.FarmerImageStorage;
import com.fruitude.farmer.model.FarmerService;
import com.fruitude.farmer.model.FarmerVO;
import com.fruitude.vendor.model.VendorService;
import com.fruitude.vendor.model.VendorVO;

import jakarta.validation.Valid;

/**
 * 後台「資訊管理」底下的小農資訊管理。小農資料另存在 farmer 表，
 * 姓名、產地等供應商欄位由 vendor 帶出，不在這裡維護。
 */
@Controller
@RequestMapping("/admin/info/farmers")
public class FarmerController {

	private static final String LIST_VIEW = "admin/info/farmers/index";
	private static final String FORM_VIEW = "admin/info/farmers/form";

	@Autowired
	FarmerService farmerSvc;

	@Autowired
	VendorService vendorSvc;

	@Autowired
	FarmerImageStorage farmerImages;

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		// 純空白視為沒填，與供應商表單一致
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
	}

	@GetMapping({ "", "/" })
	public String list(Model model) {
		model.addAttribute("activeMenu", "info");
		model.addAttribute("farmerListData", farmerSvc.getAll());
		return LIST_VIEW;
	}

	@GetMapping("/add")
	public String add(Model model) {
		model.addAttribute("farmerForm", new FarmerForm());
		return addView(model);
	}

	@PostMapping("/insert")
	public String insert(@Valid @ModelAttribute("farmerForm") FarmerForm form, BindingResult result, Model model,
			@RequestParam(value = "farmerImage", required = false) MultipartFile farmerImage,
			RedirectAttributes redirectAttributes) throws IOException {

		// 供應商必須存在，且還沒有小農資料
		VendorVO vendor = form.getVendorId() == null ? null : vendorSvc.getOneVendor(form.getVendorId());
		if (vendor == null) {
			result.rejectValue("vendorId", "required", "供應商: 請選擇供應商");
		} else if (farmerSvc.exists(vendor.getVendorId())) {
			result.rejectValue("vendorId", "duplicate", "供應商: 此供應商已有小農資訊");
		}

		byte[] imageData = checkFarmer(form, null, farmerImage, result, model);
		if (result.hasErrors() || model.containsAttribute("farmerImageError")) {
			return addView(model);
		}

		String imagePath = null;
		if (imageData != null) {
			imagePath = farmerImages.store(imageData, FarmerImageStorage.detectExtension(imageData));
		}
		try {
			farmerSvc.add(vendor, form, imagePath);
			if (imageData != null) {
				farmerSvc.saveFarmerImageData(vendor.getVendorId(), imageData);
			}
		} catch (RuntimeException e) {
			farmerImages.delete(imagePath);
			throw e;
		}

		redirectAttributes.addFlashAttribute("success", "新增成功");
		return "redirect:/admin/info/farmers";
	}

	// 供應商修改頁只接受 POST（供應商管理系統不改），由這一頁自動送出 POST，讓小農頁的連結可以直接進入該供應商的修改頁
	@GetMapping("/vendor-edit")
	public String vendorEdit(@RequestParam("vendorId") Integer vendorId, Model model) {
		model.addAttribute("vendorId", vendorId);
		return "admin/info/farmers/vendor-edit";
	}

	@GetMapping("/edit")
	public String edit(@RequestParam("vendorId") Integer vendorId, Model model) {
		FarmerVO farmer = farmerSvc.getOne(vendorId);
		if (farmer == null) {
			return "redirect:/admin/info/farmers";
		}
		FarmerForm form = new FarmerForm();
		form.setVendorId(farmer.getVendorId());
		form.setShowInFarmers(farmer.getShowInFarmers());
		form.setGrowProduct(farmer.getGrowProduct());
		form.setFarmerSlogan(farmer.getFarmerSlogan());
		form.setFarmerStory(farmer.getFarmerStory());
		model.addAttribute("farmerForm", form);
		return editView(model, farmer);
	}

	@PostMapping("/update")
	public String update(@Valid @ModelAttribute("farmerForm") FarmerForm form, BindingResult result, Model model,
			@RequestParam(value = "farmerImage", required = false) MultipartFile farmerImage,
			RedirectAttributes redirectAttributes) throws IOException {

		// 編號被清空、竄改成不存在的編號，或資料已被刪除時回到列表
		FarmerVO farmer = form.getVendorId() == null ? null : farmerSvc.getOne(form.getVendorId());
		if (farmer == null) {
			return "redirect:/admin/info/farmers";
		}

		byte[] imageData = checkFarmer(form, farmer.getFarmerImagePath(), farmerImage, result, model);
		if (result.hasErrors() || model.containsAttribute("farmerImageError")) {
			return editView(model, farmer);
		}

		String oldImagePath = farmer.getFarmerImagePath();
		String newImagePath = null;
		if (imageData != null) {
			newImagePath = farmerImages.store(imageData, FarmerImageStorage.detectExtension(imageData));
		}
		try {
			farmerSvc.update(farmer, form, newImagePath);
			if (imageData != null) {
				farmerSvc.saveFarmerImageData(farmer.getVendorId(), imageData);
			}
		} catch (RuntimeException e) {
			farmerImages.delete(newImagePath);
			throw e;
		}
		// 換了新圖片，舊檔案不再需要
		if (newImagePath != null) {
			farmerImages.delete(oldImagePath);
		}

		redirectAttributes.addFlashAttribute("success", "修改成功");
		return "redirect:/admin/info/farmers";
	}

	// 檢查品名、標語、小農故事與小農圖片；回傳這次新上傳的圖片內容（沒有上傳為 null）。
	// 圖片不合格時在 model 放入 farmerImageError。只有勾選「顯示在小農資訊」才要求必填，
	// 但已經上傳的圖片不論有沒有勾選都要檢查
	private byte[] checkFarmer(FarmerForm form, String currentImagePath, MultipartFile farmerImage,
			BindingResult result, Model model) throws IOException {
		boolean required = Boolean.TRUE.equals(form.getShowInFarmers());

		if (required) {
			if (form.getGrowProduct() == null) {
				result.rejectValue("growProduct", "required", "品名: 請勿空白");
			}
			if (form.getFarmerSlogan() == null) {
				result.rejectValue("farmerSlogan", "required", "標語: 請勿空白");
			}
			if (form.getFarmerStory() == null) {
				result.rejectValue("farmerStory", "required", "小農故事: 請勿空白");
			}
		}

		if (farmerImage == null || farmerImage.isEmpty()) {
			if (required && currentImagePath == null) {
				model.addAttribute("farmerImageError", "小農圖片: 請上傳圖片");
			}
			return null;
		}

		byte[] data = farmerImage.getBytes();
		try {
			FarmerImageStorage.detectExtension(data);
		} catch (IllegalArgumentException e) {
			model.addAttribute("farmerImageError", "小農圖片: " + e.getMessage());
			return null;
		}
		return data;
	}

	// 新增表單：下拉選單列出全部已啟用的供應商，已經有小農資料的標示並禁選
	private String addView(Model model) {
		model.addAttribute("activeMenu", "info");
		model.addAttribute("vendorOptions", farmerSvc.getVendorOptions());
		model.addAttribute("editing", false);
		return FORM_VIEW;
	}

	private String editView(Model model, FarmerVO farmer) {
		model.addAttribute("activeMenu", "info");
		model.addAttribute("farmer", farmer);
		model.addAttribute("editing", true);
		return FORM_VIEW;
	}
}
