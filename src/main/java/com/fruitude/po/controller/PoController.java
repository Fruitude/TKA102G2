package com.fruitude.po.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.employee.model.Employee;
import com.fruitude.po.model.PoService;
import com.fruitude.po.model.PoVO;
import com.fruitude.podetail.model.PoDetailVO;
import com.fruitude.product.model.ProductSku;
import com.fruitude.product.model.ProductSkuService;
import com.fruitude.vendor.model.VendorService;
import com.fruitude.vendor.model.VendorVO;

@Controller
@RequestMapping("/admin/psi/purchase")
public class PoController {
	
	@Autowired
	PoService poSvc;

	@Autowired
	ProductSkuService productSkuSvc;

	@Autowired
	VendorService vendorSvc;

	// Spring MVC 處理 @Valid 時用的同一個驗證器；insert 要先填好欄位再驗，所以自己呼叫
	@Autowired
	@Qualifier("mvcValidator")
	Validator validator;

	// 新增採購單頁面：由 purchase/index 的「新增採購單」連過來
	@GetMapping("/addPo")
	public String addPo(Model model) {
		PoVO poVO = new PoVO();
		model.addAttribute("poVO", poVO);
		// 明細的商品規格不在這裡給，頁面選了供應商後再向 skuOptions 查
		addFormData(model, poVO);

		return "admin/psi/purchase/addPo"; //view
	}

	// 新增採購單：檢查都在後端，錯誤記在 BindingResult，由 addPo.html 顯示在對應欄位下方
	// 參數不加 @Valid：採購單編號、採購日期、小計、總金額是這裡才填的，要先填好再驗，否則會被誤判成空白
	// 數量、單價輸入的不是整數時，Spring 在綁定階段就已經把錯誤記進 result
	@PostMapping("/insert")
	public String insert(@ModelAttribute("poVO") PoVO poVO, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		// 1. 填好不由使用者決定的欄位
		poSvc.prepareNewPo(poVO);

		// 2. 依 PoVO、PoDetailVO 上的註解驗證（poDetails 有 @Valid，會逐筆驗明細）
		validator.validate(poVO, result);

		// 3. 註解管不到的檢查
		checkNewPo(poVO, result);

		if (result.hasErrors()) {
			addFormData(model, poVO);
			return "admin/psi/purchase/addPo";
		}

		try {
			poSvc.addPo(poVO);
		} catch (DataIntegrityViolationException e) {
			// 兩人同時新增時可能產生相同的採購單編號，被資料庫的唯一限制擋下
			model.addAttribute("errorMessage", "採購單編號重複，請再送出一次");
			addFormData(model, poVO);
			return "admin/psi/purchase/addPo";
		}

		// 新增完成，redirect 到 listOnePo 顯示該筆資料（避免重新整理時重複送出表單）
		redirectAttributes.addFlashAttribute("success", "新增成功");
		return redirectToListOnePo(poVO.getPoId(), null);
	}

	// 新增採購單時註解管不到的檢查，錯誤以 rejectValue 記在對應欄位
	// 下拉選單沒選時，Spring 仍會建立編號為 null 的物件，@NotNull 擋不到，所以在這裡檢查
	// 查到的供應商、員工、商品規格會換回表單物件，存檔時用的是資料庫裡的那一筆
	private void checkNewPo(PoVO poVO, BindingResult result) {
		VendorVO vendorVO = null;
		if (poVO.getVendor() == null || poVO.getVendor().getVendorId() == null) {
			result.rejectValue("vendor", "required", "請選擇供應商");
		} else {
			vendorVO = vendorSvc.getOneVendor(poVO.getVendor().getVendorId());
			if (vendorVO == null) {
				result.rejectValue("vendor", "notFound", "查無此供應商");
			} else if (!Byte.valueOf((byte) 1).equals(vendorVO.getIsActive())) {
				result.rejectValue("vendor", "inactive", "供應商未啟用，無法採購");
				vendorVO = null;
			} else {
				poVO.setVendor(vendorVO);
			}
		}

		if (poVO.getPoEmployeeId() == null || poVO.getPoEmployeeId().getEmployeeId() == null) {
			result.rejectValue("poEmployeeId", "required", "請選擇採購員工");
		} else {
			Employee employee = poSvc.getOneEmployee(poVO.getPoEmployeeId().getEmployeeId());
			if (employee == null) {
				result.rejectValue("poEmployeeId", "notFound", "查無此員工");
			} else {
				poVO.setPoEmployeeId(employee);
			}
		}

		if (poVO.getPoDetails().isEmpty()) {
			result.rejectValue("poDetails", "empty", "採購明細至少要有一筆");
		}

		Set<Integer> skuIds = new HashSet<>();
		for (int index = 0; index < poVO.getPoDetails().size(); index++) {
			PoDetailVO poDetailVO = poVO.getPoDetails().get(index);
			String skuField = "poDetails[" + index + "].skuId";

			if (poDetailVO.getSkuId() == null || poDetailVO.getSkuId().getSkuId() == null) {
				result.rejectValue(skuField, "required", "請選擇商品規格");
				continue;
			}

			ProductSku productSku = productSkuSvc.getOneProductSku(poDetailVO.getSkuId().getSkuId());
			if (productSku == null) {
				result.rejectValue(skuField, "notFound", "查無此商品規格");
				continue;
			}

			// 同一張採購單不可有重複的商品規格（資料表有 po_id + sku_id 的唯一限制）
			if (!skuIds.add(productSku.getSkuId())) {
				result.rejectValue(skuField, "duplicate", "同一張採購單不可有重複的商品規格");
				continue;
			}

			// 下拉選單只列所選供應商的規格，這裡擋直接送出其他編號的情況；供應商本身有錯時不比對
			if (vendorVO != null && (productSku.getProduct() == null || productSku.getProduct().getVendor() == null
					|| !productSku.getProduct().getVendor().getVendorId().equals(vendorVO.getVendorId()))) {
				result.rejectValue(skuField, "wrongVendor", "商品規格不屬於所選的供應商");
				continue;
			}

			if (ProductSku.STATUS_DISCONTINUED.equals(productSku.getStatus())) {
				result.rejectValue(skuField, "discontinued", "商品規格已永久停產，無法採購");
				continue;
			}

			poDetailVO.setSkuId(productSku);
		}
	}

	// 新增頁需要的資料：供應商與採購員工的下拉選單、顯示用的採購日期（今天）
	// 檢查失敗重新顯示時，已選供應商的商品規格清單也一併給頁面，明細列才能還原選中的規格
	private void addFormData(Model model, PoVO poVO) {
		poVO.setOrderDate(LocalDateTime.now()); // 只用來顯示；實際存入的時間由 prepareNewPo 在送出時決定

		model.addAttribute("vendorListData", vendorSvc.getActiveVendors());
		model.addAttribute("employeeListData", poSvc.getAllEmployees());

		List<Map<String, Object>> skuOptionData = new ArrayList<>();
		if (poVO.getVendor() != null && poVO.getVendor().getVendorId() != null) {
			skuOptionData = skuOptions(poVO.getVendor().getVendorId());
		}
		model.addAttribute("skuOptionData", skuOptionData);
	}

	// 新增採購單頁面選了供應商後以 fetch 呼叫，回傳該供應商可採購的商品規格（JSON）
	// 只回傳下拉選單需要的 skuId、displayName，不直接回傳 ProductSku（有 LAZY 關聯，不適合直接轉 JSON）
	@GetMapping("/skuOptions")
	@ResponseBody
	public List<Map<String, Object>> skuOptions(@RequestParam("vendorId") Integer vendorId) {
		List<Map<String, Object>> skuOptions = new ArrayList<>();
		for (ProductSku productSku : productSkuSvc.getPurchasableByVendorId(vendorId)) {
			Map<String, Object> skuOption = new LinkedHashMap<>();
			skuOption.put("skuId", productSku.getSkuId());
			skuOption.put("displayName", productSku.getDisplayName());
			skuOptions.add(skuOption);
		}
		return skuOptions;
	}

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

	// 修改採購明細：流程比照 insert，錯誤記在 BindingResult，由 updatePo.html 顯示在對應欄位下方
	// 參數不加 @Valid：表單只送明細的規格、數量、單價，其餘欄位要先從資料庫補回來再驗，否則會被誤判成空白
	// 這裡的 poStatus 是列表的篩選條件，不是要存入的採購單狀態；它和 PoVO 的 poStatus 同名，會一併綁進 poVO，
	// 但 prepareUpdatePo 會以資料庫的狀態蓋回去，updatePoWithDetails 也不會寫入狀態
	@PostMapping("/update")
	public String update(@ModelAttribute("poVO") PoVO poVO, BindingResult result, Model model,
			@RequestParam(value = "poStatus", required = false) Byte poStatus,
			RedirectAttributes redirectAttributes) {

		// 1. 從資料庫補回表單沒送的欄位；採購單不存在、不是待審核、明細對不起來時不是欄位填錯，回單筆頁顯示原因
		try {
			poSvc.prepareUpdatePo(poVO);
		} catch (IllegalArgumentException e) {
			return redirectWithError(poVO.getPoId(), poStatus, e.getMessage(), redirectAttributes);
		}

		// 2. 依 PoVO、PoDetailVO 上的註解驗證（poDetails 有 @Valid，會逐筆驗明細）
		validator.validate(poVO, result);

		// 3. 註解管不到的檢查
		checkUpdatePo(poVO, result);

		if (result.hasErrors()) {
			model.addAttribute("poStatus", poStatus);
			model.addAttribute("skuListData", productSkuSvc.getPurchasableByVendorId(poVO.getVendor().getVendorId()));
			return "admin/psi/purchase/updatePo";
		}

		// 存檔時 updatePoWithDetails 會以資料庫的資料再檢查一次，不通過時回單筆頁顯示原因
		try {
			poSvc.updatePoWithDetails(poVO);
		} catch (IllegalArgumentException e) {
			return redirectWithError(poVO.getPoId(), poStatus, e.getMessage(), redirectAttributes);
		}

		redirectAttributes.addFlashAttribute("success", "修改成功");
		return redirectToListOnePo(poVO.getPoId(), poStatus);
	}

	// 修改採購明細時註解管不到的檢查，錯誤以 rejectValue 記在對應欄位
	// 呼叫前 prepareUpdatePo 已確認每筆表單明細都對得上資料庫的明細
	private void checkUpdatePo(PoVO poVO, BindingResult result) {
		Map<Integer, PoDetailVO> dbDetails = new HashMap<>();
		for (PoDetailVO dbDetail : poSvc.getOnePo(poVO.getPoId()).getPoDetails()) {
			dbDetails.put(dbDetail.getPoDetailId(), dbDetail);
		}

		Set<Integer> skuIds = new HashSet<>();
		for (int index = 0; index < poVO.getPoDetails().size(); index++) {
			PoDetailVO formDetail = poVO.getPoDetails().get(index);
			PoDetailVO dbDetail = dbDetails.get(formDetail.getPoDetailId());
			String skuField = "poDetails[" + index + "].skuId";

			if (formDetail.getQuantity() != null && formDetail.getQuantity() < dbDetail.getArrivedPcs()) {
				result.rejectValue("poDetails[" + index + "].quantity", "belowArrived", "採購數量不可小於已到貨數量");
			}

			Integer formSkuId = formDetail.getSkuId().getSkuId();
			if (formSkuId == null) {
				result.rejectValue(skuField, "required", "請選擇商品規格");
				continue;
			}

			// 同一張採購單不可有重複的商品規格（資料表有 po_id + sku_id 的唯一限制）
			if (!skuIds.add(formSkuId)) {
				result.rejectValue(skuField, "duplicate", "同一張採購單不可有重複的商品規格");
				continue;
			}

			// 規格沒換的明細即使原規格已停產也照原樣保留，不再檢查
			if (formSkuId.equals(dbDetail.getSkuId().getSkuId())) {
				formDetail.setSkuId(dbDetail.getSkuId());
				continue;
			}

			ProductSku productSku = productSkuSvc.getOneProductSku(formSkuId);
			if (productSku == null) {
				result.rejectValue(skuField, "notFound", "查無此商品規格");
				continue;
			}

			// 下拉選單只列這張單供應商的規格，這裡擋直接送出其他編號的情況
			if (productSku.getProduct() == null || productSku.getProduct().getVendor() == null
					|| !productSku.getProduct().getVendor().getVendorId().equals(poVO.getVendor().getVendorId())) {
				result.rejectValue(skuField, "wrongVendor", "商品規格不屬於此採購單的供應商");
				continue;
			}

			if (ProductSku.STATUS_DISCONTINUED.equals(productSku.getStatus())) {
				result.rejectValue(skuField, "discontinued", "商品規格已永久停產，無法採購");
				continue;
			}

			formDetail.setSkuId(productSku);
		}
	}

	// 帶著錯誤訊息回單筆頁；連採購單編號都沒有時回列表
	private String redirectWithError(Integer poId, Byte poStatus, String errorMessage,
			RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
		if (poId == null) {
			return "redirect:/admin/psi/purchase/listAllPo";
		}
		return redirectToListOnePo(poId, poStatus);
	}

	// 回單筆頁的網址；poStatus（列表的篩選條件）有值才帶
	private String redirectToListOnePo(Integer poId, Byte poStatus) {
		String url = "redirect:/admin/psi/purchase/listOnePo?poId=" + poId;
		return poStatus == null ? url : url + "&poStatus=" + poStatus;
	}
	
	@InitBinder
	public void initBinder(WebDataBinder binder) {
	    // true 代表：空字串或只有空白的字串，轉成 null
	    binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
	}

}
