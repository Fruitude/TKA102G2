package com.fruitude.po.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.po.model.PoService;
import com.fruitude.po.model.PoVO;
import com.fruitude.product.model.ProductSkuService;

@Controller
@RequestMapping("/admin/psi/purchase")
public class PoController {
	
	@Autowired
	PoService poSvc;

	@Autowired
	ProductSkuService productSkuSvc;

	@RequestMapping("/listAllPo")
	public String listAllVendor(Model model,
	        @RequestParam(value = "poStatus", required = false) Byte poStatus) {

	    List<PoVO> pos = (poStatus == null)
	            ? poSvc.getAll()
	            : poSvc.getByPoStatus(poStatus);

	    if (pos.isEmpty()) {
	        model.addAttribute("errorMessage", "查無資料");
	        return "admin/psi/purchase/index";
	    }
	    model.addAttribute("poStatus", poStatus);
	    model.addAttribute("poListData", pos);
	    return "admin/psi/purchase/listAllPo";
	}
	
	// 新增、修改成功後 redirect 過來，依 vendorId 顯示單筆資料
	@GetMapping("/listOnePo")
	public String listOneVendor(@RequestParam("poId") Integer poId, 
			@RequestParam(value = "poStatus", required = false) Byte poStatus,
			Model model) {
		PoVO poVO = poSvc.getOnePo(poId);

		// 資料已被刪除時回到列表
		if (poVO == null) {
			return "redirect:/admin/psi/purchase/listAllPo";
		}
		model.addAttribute("poStatus", poStatus);
		model.addAttribute("poVO", poVO);

		return "admin/psi/purchase/listOnePo"; //view
	}
	
	@PostMapping("/getOne_For_Update")
	public String getOne_For_Update(@RequestParam("poId") Integer poId,
			@RequestParam(value = "poStatus", required = false) Byte poStatus,
			Model model) {
		PoVO poVO = poSvc.getOnePo(poId);

		// 資料已被刪除時回到列表
		if (poVO == null) {
			return "redirect:/admin/psi/purchase/listAllPo";
		}

		// 不是待審核的採購單不進修改頁，改顯示單筆資料
		if (!poVO.isEditable()) {
			return redirectToListOnePo(poId, poStatus);
		}

		// poStatus 是列表的篩選條件，一路帶著，修改完回列表時才能維持原本的篩選
		model.addAttribute("poStatus", poStatus);
		model.addAttribute("poVO", poVO);
		// 明細的商品規格下拉選單：這張單的供應商底下、未永久停產的規格
		model.addAttribute("skuListData", productSkuSvc.getPurchasableByVendorId(poVO.getVendor().getVendorId()));

		return "admin/psi/purchase/updatePo"; //view
	}

	@PostMapping("/update")
	public String update(@ModelAttribute("poVO") PoVO poVO,
			@RequestParam(value = "poStatus", required = false) Byte poStatus,
			RedirectAttributes redirectAttributes) {
		// 這裡的 poStatus 是列表的篩選條件，不是要存入的採購單狀態；updatePoWithDetails 不會寫入狀態
		try {
			poSvc.updatePoWithDetails(poVO);
		} catch (IllegalArgumentException e) {
			// 狀態不符或明細檢查不通過：回單筆頁顯示原因，該頁會重新從資料庫取資料
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return redirectToListOnePo(poVO.getPoId(), poStatus);
		}

		redirectAttributes.addFlashAttribute("success", "修改成功");
		return redirectToListOnePo(poVO.getPoId(), poStatus);
	}

	// 回單筆頁的網址；poStatus（列表的篩選條件）有值才帶
	private String redirectToListOnePo(Integer poId, Byte poStatus) {
		String url = "redirect:/admin/psi/purchase/listOnePo?poId=" + poId;
		return poStatus == null ? url : url + "&poStatus=" + poStatus;
	}
	

}
