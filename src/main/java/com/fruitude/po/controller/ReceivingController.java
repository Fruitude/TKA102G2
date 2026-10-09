package com.fruitude.po.controller;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.employee.model.Employee;
import com.fruitude.po.model.PoVO;
import com.fruitude.po.model.ReceivingService;
import com.fruitude.podetail.model.PoDetailVO;

import jakarta.servlet.http.HttpSession;

// 進貨系統：和採購單共用 PoVO、PoDetailVO，處理審核通過後的到貨、驗收、入庫
@Controller
@RequestMapping("/admin/psi/receiving")
public class ReceivingController {

	@Autowired
	ReceivingService receivingSvc;

	// 進貨系統首頁：列出申請通過、尚未驗收的採購單
	@GetMapping("")
	public String receiving(Model model) {
		model.addAttribute("poListData", receivingSvc.getPendingInboundPos());
		return "admin/psi/receiving/index"; //view
	}

	// 單筆資料：由首頁列表的「詳細」連過來，依 poId 顯示採購單與明細
	@GetMapping("/listOneReceiving")
	public String listOneReceiving(@RequestParam("poId") Integer poId, Model model) {
		PoVO poVO = receivingSvc.getOneApprovedPo(poId);

		// 查不到，或不是申請通過的採購單時回首頁
		if (poVO == null) {
			return "redirect:/admin/psi/receiving";
		}
		model.addAttribute("poVO", poVO);

		return "admin/psi/receiving/listOneReceiving"; //view
	}

	// 驗收頁：由單筆頁的「驗收」按鈕連過來
	@PostMapping("/getOneForUpdate")
	public String getOneForUpdate(@RequestParam("poId") Integer poId, Model model, HttpSession session) {
		PoVO poVO = receivingSvc.getOneApprovedPo(poId);

		// 查不到，或不是申請通過的採購單時回首頁
		if (poVO == null) {
			return "redirect:/admin/psi/receiving";
		}

		// 已經驗收過的採購單不進驗收頁，改顯示單筆資料
		if (!poVO.isReceivable()) {
			return redirectToListOneReceiving(poId);
		}

		model.addAttribute("poVO", poVO);
		addReceivingData(model, getLoggedInEmployee(session));

		return "admin/psi/receiving/updateReceiving"; //view
	}

	// 驗收存檔：流程比照 PoController 的 update，錯誤記在 BindingResult，由 updateReceiving.html 顯示在對應欄位下方
	// 表單只送驗收狀態、每筆明細的到貨數量與不良品數量；驗收員工、驗收日期、入庫數量、金額都不由表單決定
	// 到貨數量、不良品數量輸入的不是整數時，Spring 在綁定階段就已經把錯誤記進 result
	@PostMapping("/update")
	public String update(@ModelAttribute("poVO") PoVO poVO, BindingResult result, Model model,
			HttpSession session, RedirectAttributes redirectAttributes) {

		// 1. 從資料庫補回表單沒送的欄位，並算出入庫數量與金額；採購單不存在、不是待驗收時不是欄位填錯，回單筆頁顯示原因
		try {
			receivingSvc.prepareReceiving(poVO);
		} catch (IllegalArgumentException e) {
			return redirectWithError(poVO.getPoId(), e.getMessage(), redirectAttributes);
		}

		// 驗收員工不由表單決定，一律使用登入的員工
		Employee inboundEmployee = getLoggedInEmployee(session);
		addReceivingData(model, inboundEmployee);
		if (inboundEmployee == null) {
			// 登入逾時或沒登入：不是欄位填錯，顯示在頁面上方，使用者填的內容保留
			model.addAttribute("errorMessage", "尚未登入後台或登入已逾時，請重新登入後再送出");
			return "admin/psi/receiving/updateReceiving";
		}

		// 2. 檢查驗收狀態與每筆明細的數量
		checkReceiving(poVO, result);

		if (result.hasErrors()) {
			return "admin/psi/receiving/updateReceiving";
		}

		// 存檔時 receive 會以資料庫的資料再檢查一次，不通過時回單筆頁顯示原因
		try {
			receivingSvc.receive(poVO, inboundEmployee);
		} catch (IllegalArgumentException e) {
			return redirectWithError(poVO.getPoId(), e.getMessage(), redirectAttributes);
		} catch (DataIntegrityViolationException e) {
			// 資料庫的限制擋下這次驗收；整筆已回滾，回單筆頁顯示而不是出現錯誤頁
			return redirectWithError(poVO.getPoId(), "資料庫拒絕這次驗收，請重新進入驗收頁再試一次", redirectAttributes);
		}

		redirectAttributes.addFlashAttribute("success", "驗收完成");
		return redirectToListOneReceiving(poVO.getPoId());
	}

	// 驗收的檢查，錯誤以 rejectValue 記在對應欄位
	// 該欄位在綁定階段已經有錯誤（輸入的不是整數）時不再多記一筆
	private void checkReceiving(PoVO poVO, BindingResult result) {
		Byte inboundStatus = poVO.getInboundStatus();
		if (!result.hasFieldErrors("inboundStatus")
				&& (inboundStatus == null || (inboundStatus != 1 && inboundStatus != 2))) {
			result.rejectValue("inboundStatus", "required", "請選擇驗收狀態");
		}

		// 驗收失敗時數量已經由 ReceivingService 的 prepareReceiving 歸零，不用檢查使用者填的數量
		if (receivingSvc.isInboundFailed(inboundStatus)) {
			return;
		}

		for (int index = 0; index < poVO.getPoDetails().size(); index++) {
			PoDetailVO poDetailVO = poVO.getPoDetails().get(index);
			String arrivedField = "poDetails[" + index + "].arrivedPcs";
			String defectField = "poDetails[" + index + "].defectPcs";
			Integer arrivedPcs = poDetailVO.getArrivedPcs();
			Integer defectPcs = poDetailVO.getDefectPcs();

			if (!result.hasFieldErrors(arrivedField)) {
				if (arrivedPcs == null) {
					result.rejectValue(arrivedField, "required", "到貨數量，請勿空白");
				} else if (arrivedPcs < 0) {
					result.rejectValue(arrivedField, "negative", "到貨數量不可為負數");
				} else if (arrivedPcs > poDetailVO.getQuantity()) {
					result.rejectValue(arrivedField, "aboveQuantity", "到貨數量不可大於採購數量");
				}
			}

			if (!result.hasFieldErrors(defectField)) {
				if (defectPcs == null) {
					result.rejectValue(defectField, "required", "不良品數量，請勿空白");
				} else if (defectPcs < 0) {
					result.rejectValue(defectField, "negative", "不良品數量不可為負數");
				} else if (arrivedPcs != null && defectPcs > arrivedPcs) {
					result.rejectValue(defectField, "aboveArrived", "不良品數量不可大於到貨數量");
				}
			}
		}
	}

	// 驗收頁只顯示不送出的資料：驗收員工（登入的員工）、驗收日期（現在）
	// 不放進 poVO：剛進驗收頁時 poVO 是資料庫查出來的那一筆，不為了顯示去改它；實際存入的值由 ReceivingService 的 receive 決定
	private void addReceivingData(Model model, Employee inboundEmployee) {
		model.addAttribute("inboundEmployee", inboundEmployee);
		model.addAttribute("inboundDateNow", LocalDateTime.now());
	}

	// 目前登入後台的員工（EmployeeSessionController 登入時放進 session 的 loggedInEmployeeId）
	// 沒登入、登入逾時或查無此員工時回傳 null
	private Employee getLoggedInEmployee(HttpSession session) {
		Object employeeId = session.getAttribute("loggedInEmployeeId");
		return employeeId instanceof Number ? receivingSvc.getOneEmployee(((Number) employeeId).intValue()) : null;
	}

	// 帶著錯誤訊息回單筆頁；連採購單編號都沒有時回首頁
	private String redirectWithError(Integer poId, String errorMessage, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
		if (poId == null) {
			return "redirect:/admin/psi/receiving";
		}
		return redirectToListOneReceiving(poId);
	}

	// 回單筆頁的網址
	private String redirectToListOneReceiving(Integer poId) {
		return "redirect:/admin/psi/receiving/listOneReceiving?poId=" + poId;
	}

	// 表單只接受這幾個欄位，其餘欄位即使請求有帶也不會綁進 poVO
	@InitBinder("poVO")
	public void initBinder(WebDataBinder binder) {
		binder.setAllowedFields("poId", "inboundStatus", "poDetails[*].poDetailId", "poDetails[*].arrivedPcs",
				"poDetails[*].defectPcs");
	}

}
