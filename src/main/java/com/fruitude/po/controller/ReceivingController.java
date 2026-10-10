package com.fruitude.po.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
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

import jakarta.servlet.http.HttpSession;

// 進貨系統：和採購單共用 PoVO、PoDetailVO，處理審核通過後的到貨、驗收、入庫
@Controller
@RequestMapping("/admin/psi/receiving")
public class ReceivingController {

	@Autowired
	ReceivingService receivingSvc;

	// 把綁定階段的錯誤（輸入的不是整數）換成 messages.properties 裡的訊息
	@Autowired
	MessageSource messageSource;

	// 明細的到貨數量、不良品數量欄位名：poDetails[索引].arrivedPcs、poDetails[索引].defectPcs
	private static final Pattern DETAIL_QUANTITY_FIELD = Pattern.compile("poDetails\\[(\\d+)\\]\\.(arrivedPcs|defectPcs)");

	// 進貨系統首頁：列出申請通過、尚未驗收的採購單
	@GetMapping("")
	public String receiving(Model model) {
		model.addAttribute("poListData", receivingSvc.getPendingInboundPos());
		return "admin/psi/receiving/index"; //view
	}

	// 驗收紀錄：由首頁的「查詢驗收紀錄」連過來，列出所有已結案（驗收過）的採購單
	@GetMapping("/listAllReceiving")
	public String listAllReceiving(Model model) {
		model.addAttribute("poListData", receivingSvc.getClosedPos());
		return "admin/psi/receiving/listAllReceiving"; //view
	}

	// 單筆資料：由首頁列表、驗收紀錄的「詳細」連過來，依 poId 顯示採購單與明細
	@GetMapping("/listOneReceiving")
	public String listOneReceiving(@RequestParam("poId") Integer poId, Model model) {
		PoVO poVO = receivingSvc.getOneApprovedPo(poId);

		// 查不到，或不是申請通過、已結案的採購單時回首頁
		if (poVO == null) {
			return "redirect:/admin/psi/receiving";
		}
		model.addAttribute("poVO", poVO);

		return "admin/psi/receiving/listOneReceiving"; //view
	}

	// 驗收頁：由單筆頁的「驗收」按鈕，或驗收紀錄、單筆頁的「修改」按鈕連過來
	// 待驗收的採購單是第一次驗收，數量欄位空白；已結案的採購單是修改驗收紀錄，帶入上次存的內容
	@PostMapping("/getOneForUpdate")
	public String getOneForUpdate(@RequestParam("poId") Integer poId, Model model, HttpSession session) {
		PoVO poVO = receivingSvc.getOneApprovedPo(poId);

		// 查不到，或不是申請通過、已結案的採購單時回首頁
		if (poVO == null) {
			return "redirect:/admin/psi/receiving";
		}

		// 不是待驗收、也不是已結案的採購單不進驗收頁，改顯示單筆資料
		if (!poVO.isReceivable() && !poVO.isClosed()) {
			return redirectToListOneReceiving(poId);
		}

		// 交給頁面的是另外建立的表單物件，不是資料庫查出來的那一筆
		model.addAttribute("poVO", receivingSvc.getReceivingForm(poVO));
		// 使用者這次看到的驗收內容摘要，隨表單送回 update，用來擋下驗收頁開著的期間被別人驗收或修改過的採購單
		model.addAttribute("receivedContent", receivingSvc.getReceivedContent(poVO));
		addReceivingData(model, getLoggedInEmployee(session));

		return "admin/psi/receiving/updateReceiving"; //view
	}

	// 驗收存檔（第一次驗收與修改驗收紀錄共用）：檢查的邏輯在 ReceivingService，有錯誤就回到 updateReceiving.html
	// 驗收狀態的錯誤顯示在下拉選單下方；到貨數量、不良品數量的錯誤整理成一段文字，顯示在「採購明細」的旁邊
	// 表單只送驗收狀態、每筆明細的到貨數量與不良品數量；驗收員工、驗收日期、入庫數量、金額都不由表單決定
	// receivedContent 是驗收頁顯示當時的內容摘要；沒有帶或和資料庫當下的內容不一樣時不存檔
	@PostMapping("/update")
	public String update(@ModelAttribute("poVO") PoVO poVO, BindingResult result, Model model,
			@RequestParam(value = "receivedContent", required = false) String receivedContent,
			HttpSession session, RedirectAttributes redirectAttributes) {

		// 檢查失敗重新顯示驗收頁時，表單要繼續帶著一開始的內容摘要，不能換成現在的
		model.addAttribute("receivedContent", receivedContent);

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

		// 2. 檢查驗收狀態
		boolean inboundStatusInvalid = result.hasFieldErrors("inboundStatus");
		if (!inboundStatusInvalid && !receivingSvc.isValidInboundStatus(poVO.getInboundStatus())) {
			result.rejectValue("inboundStatus", "required", "請選擇驗收狀態");
			inboundStatusInvalid = true;
		}

		// 3. 檢查每筆明細的數量：輸入的不是整數時，Spring 在綁定階段就已經記下錯誤，先顯示這些；
		//    都是整數才交給 ReceivingService 的 checkReceiving 檢查空白、範圍、整張單至少一筆到貨
		List<String> detailErrors = getDetailBindingErrors(result);
		if (detailErrors.isEmpty()) {
			detailErrors = receivingSvc.checkReceiving(poVO);
		}

		if (inboundStatusInvalid || !detailErrors.isEmpty()) {
			if (!detailErrors.isEmpty()) {
				// 一筆錯誤一行，頁面以 white-space: pre-line 換行顯示
				model.addAttribute("detailErrorMessage", String.join("\n", detailErrors));
			}
			return "admin/psi/receiving/updateReceiving";
		}

		// 存檔時 receive 會以資料庫的資料再檢查一次，不通過時回單筆頁顯示原因
		boolean modified;
		try {
			modified = receivingSvc.receive(poVO, inboundEmployee, receivedContent);
		} catch (IllegalArgumentException e) {
			return redirectWithError(poVO.getPoId(), e.getMessage(), redirectAttributes);
		} catch (DataIntegrityViolationException e) {
			// 資料庫的限制擋下這次驗收；整筆已回滾，回單筆頁顯示而不是出現錯誤頁
			return redirectWithError(poVO.getPoId(), "資料庫拒絕這次驗收，請重新進入驗收頁再試一次", redirectAttributes);
		}

		redirectAttributes.addFlashAttribute("success", modified ? "驗收紀錄已修改" : "驗收完成");
		return redirectToListOneReceiving(poVO.getPoId());
	}

	// 到貨數量、不良品數量在綁定階段的錯誤（輸入的不是整數），整理成「第幾筆：訊息」的文字
	// 訊息取自 messages.properties 的 typeMismatch.poVO.poDetails.arrivedPcs、defectPcs
	private List<String> getDetailBindingErrors(BindingResult result) {
		List<String> errorMessages = new ArrayList<>();
		for (FieldError fieldError : result.getFieldErrors()) {
			Matcher matcher = DETAIL_QUANTITY_FIELD.matcher(fieldError.getField());
			if (matcher.matches()) {
				int rowNumber = Integer.parseInt(matcher.group(1)) + 1;
				errorMessages.add("第 " + rowNumber + " 筆："
						+ messageSource.getMessage(fieldError, LocaleContextHolder.getLocale()));
			}
		}
		return errorMessages;
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
