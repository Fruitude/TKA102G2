package com.fruitude.vendor.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.vendor.model.VendorService;
import com.fruitude.vendor.model.VendorVO;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/psi/vendor")
public class VendorController {

	@Autowired
	VendorService vendorSvc;

	@RequestMapping("/listAllVendor")
	public String listAllVendor(Model model,
	        @RequestParam(value = "isActive", required = false) Byte isActive) {

	    List<VendorVO> vendors = (isActive == null)
	            ? vendorSvc.getAll()
	            : vendorSvc.getByIsActive(isActive);

	    if (vendors.isEmpty()) {
	        model.addAttribute("errorMessage", "查無資料");
	        return "admin/psi/vendor/index";
	    }
	    model.addAttribute("isActive", isActive);
	    model.addAttribute("vendorListData", vendors);
	    return "admin/psi/vendor/listAllVendor";
	}



	@PostMapping("/insert")
	public String insert(@Valid VendorVO vendorVO, BindingResult result, Model model,
			@RequestParam("upFiles") MultipartFile part, RedirectAttributes redirectAttributes) throws IOException {

		// 新增時編號由資料庫產生，不採用請求帶來的 vendorId（避免覆蓋既有資料）
		vendorVO.setVendorId(null);

		// 統一編號為非必填，有填寫時不可與其他供應商重複
		if (vendorVO.getTaxId() != null && vendorSvc.existsByTaxId(vendorVO.getTaxId())) {
			result.rejectValue("taxId", "duplicate", "統一編號: 已存在，請勿重複");
		}

		// 品牌標誌為非必填
		boolean logoOk = checkLogo(vendorVO, part, model);

		if (result.hasErrors() || !logoOk) {
			return "admin/psi/vendor/addVendor";
		}
		
		
		vendorSvc.addVendor(vendorVO);

		// 新增完成，redirect 到 listOneVendor 顯示該筆資料（避免重新整理時重複送出表單）
		redirectAttributes.addFlashAttribute("success", "新增成功");
		redirectAttributes.addAttribute("vendorId", vendorVO.getVendorId());

		return "redirect:/admin/psi/vendor/listOneVendor";
	}

	// 新增、修改成功後 redirect 過來，依 vendorId 顯示單筆資料
	@GetMapping("/listOneVendor")
	public String listOneVendor(@RequestParam("vendorId") Integer vendorId, Model model) {
		VendorVO vendorVO = vendorSvc.getOneVendor(vendorId);

		// 資料已被刪除時回到列表
		if (vendorVO == null) {
			return "redirect:/admin/psi/vendor/listAllVendor";
		}

		model.addAttribute("vendorVO", vendorVO);

		return "admin/psi/vendor/listOneVendor"; //view
	}

	@PostMapping("/getOne_For_Update")
	public String getOne_For_Update(@RequestParam("vendorId") Integer vendorId, Model model) {
		VendorVO vendorVO = vendorSvc.getOneVendor(vendorId);

		// 資料已被刪除時回到列表
		if (vendorVO == null) {
			return "redirect:/admin/psi/vendor/listAllVendor";
		}

		model.addAttribute("vendorVO", vendorVO);

		return "admin/psi/vendor/updateVendor"; //view
	}

	@PostMapping("/update")
	public String update(@Valid VendorVO vendorVO, BindingResult result, Model model,
			@RequestParam("upFiles") MultipartFile part, RedirectAttributes redirectAttributes) throws IOException {

		// 編號被清空、竄改成不存在的編號，或資料已被刪除時回到列表
		if (vendorVO.getVendorId() == null || vendorSvc.getOneVendor(vendorVO.getVendorId()) == null) {
			return "redirect:/admin/psi/vendor/listAllVendor";
		}

		// 統一編號為非必填，有填寫時不可與其他供應商重複（排除自己）
		if (vendorVO.getTaxId() != null
				&& vendorSvc.existsByTaxIdAndVendorIdNot(vendorVO.getTaxId(), vendorVO.getVendorId())) {
			result.rejectValue("taxId", "duplicate", "統一編號: 已存在，請勿重複");
		}

		// 可以不修改圖片，沒有上傳時 logo 維持 null，由 updateVendor 保留原圖
		boolean logoOk = checkLogo(vendorVO, part, model);

		if (result.hasErrors() || !logoOk) {
			return "admin/psi/vendor/updateVendor";
		}

		vendorSvc.updateVendor(vendorVO);

		// 修改完成，redirect 到 listOneVendor 顯示該筆資料（避免重新整理時重複送出表單）
		redirectAttributes.addFlashAttribute("success", "修改成功");
		redirectAttributes.addAttribute("vendorId", vendorVO.getVendorId());

		return "redirect:/admin/psi/vendor/listOneVendor";
	}

	// 有上傳品牌標誌時存入 vendorVO，只接受圖片；不是圖片時放入錯誤訊息並回傳 false
	private boolean checkLogo(VendorVO vendorVO, MultipartFile part, Model model) throws IOException {
		if (part.isEmpty()) {
			return true;
		}

		String contentType = part.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			model.addAttribute("errorMessage", "品牌標誌: 只能上傳圖片檔");
			return false;
		}

		vendorVO.setLogo(part.getBytes());
		return true;
	}

	@InitBinder
	public void initBinder(WebDataBinder binder) {
	    // true 代表：空字串或只有空白的字串，轉成 null
	    binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
	}

}
