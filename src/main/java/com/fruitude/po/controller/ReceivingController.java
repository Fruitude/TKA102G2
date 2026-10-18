package com.fruitude.po.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.employee.model.Employee;
import com.fruitude.po.model.PoVO;
import com.fruitude.po.model.ReceivingService;
import com.fruitude.utils.PsiUtils;

import jakarta.servlet.http.HttpSession;

// 進貨系統：和採購單共用 PoVO、PoDetailVO，處理審核通過後的到貨、驗收、入庫
@Controller
@RequestMapping("/admin/psi/receiving")
public class ReceivingController {
	@Autowired
	com.fruitude.product.model.ProductPurchaseFlowService productPurchaseFlow;


	@Autowired
	ReceivingService receivingSvc;

	// 把綁定階段的錯誤（輸入的不是整數）換成 messages.properties 裡的訊息
	@Autowired
	MessageSource messageSource;

	// 明細的到貨數量、不良品數量欄位名：poDetails[索引].arrivedPcs、poDetails[索引].defectPcs
	private static final Pattern DETAIL_QUANTITY_FIELD = Pattern.compile("poDetails\\[(\\d+)\\]\\.(arrivedPcs|defectPcs)");

	// 歷史驗收紀錄查詢的供應商編號上限，和採購系統的查詢（PoNoController）相同
	private static final int VENDOR_ID_MAX = 99999;

	// 單筆頁、驗收頁查不到採購單時的訊息；ReceivingService 的 getOneApprovedPo 對「不存在」和「狀態不符」都回傳 null，分不出是哪一種
	private static final String PO_NOT_FOUND_MESSAGE = "查無此採購單，或這張採購單不是申請通過、已結案的狀態";

	// 進貨系統首頁：列出申請通過、尚未驗收的採購單
	@GetMapping("")
	public String receiving(Model model) {
		model.addAttribute("poListData", receivingSvc.getPendingInboundPos());
		return "admin/psi/receiving/index"; //view
	}

	// 歷史驗收紀錄：由首頁的「查詢驗收紀錄」連過來，列出已結案（驗收過）的採購單，每頁 10 筆；page 是頁碼，從 1 開始
	// 查詢條件都是選填，由頁面上方的查詢表單送過來；翻頁時頁碼連結會帶著同樣的條件
	// 查詢條件用 String 接，在 validateReceivingSearch 檢查格式，網址被改成 abc 之類的值也能顯示原因
	// 輸入不正確：所有錯誤一行一個顯示在紅框，列表改列出全部紀錄的第 1 頁
	// 條件正確但查無資料：紅框顯示「查無資料」，表格顯示查無符合條件的驗收紀錄
	@GetMapping("/listAllReceiving")
	public String listAllReceiving(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "poNo", required = false) String poNo,
			@RequestParam(value = "vendorId", required = false) String vendorId,
			@RequestParam(value = "inboundStatus", required = false) String inboundStatus,
			@RequestParam(value = "startDate", required = false) String startDate,
			@RequestParam(value = "endDate", required = false) String endDate,
			Model model) {
		// 採購單編號不分大小寫，一律轉成大寫再檢查與查詢
		String poNoText = poNo == null ? "" : poNo.trim().toUpperCase();
		String vendorIdText = vendorId == null ? "" : vendorId.trim();
		String inboundStatusText = inboundStatus == null ? "" : inboundStatus.trim();
		String startDateText = startDate == null ? "" : startDate.trim();
		String endDateText = endDate == null ? "" : endDate.trim();
		boolean searching = !poNoText.isEmpty() || !vendorIdText.isEmpty() || !inboundStatusText.isEmpty()
				|| !startDateText.isEmpty() || !endDateText.isEmpty();

		List<String> errors = validateReceivingSearch(poNoText, vendorIdText, inboundStatusText, startDateText,
				endDateText);
		Page<PoVO> poPage;
		if (!errors.isEmpty()) {
			// 條件不正確：列出全部紀錄的第 1 頁，翻頁連結不帶條件；表單由 param 回填使用者原本輸入的內容
			model.addAttribute("errorMessage", String.join("\n", errors));
			poPage = receivingSvc.searchClosedPos(1, null, null, null, null, null);
			searching = false;
		} else {
			poPage = receivingSvc.searchClosedPos(page, poNoText.isEmpty() ? null : poNoText,
					vendorIdText.isEmpty() ? null : Integer.valueOf(vendorIdText),
					inboundStatusText.isEmpty() ? null : Byte.valueOf(inboundStatusText),
					PsiUtils.parseDate(startDateText), PsiUtils.parseDate(endDateText));
			if (searching && poPage.getTotalElements() == 0) {
				model.addAttribute("errorMessage", "查無資料");
			}
			// 查詢條件：組翻頁連結用（條件不正確時不放，翻頁連結就不帶條件）
			model.addAttribute("poNo", poNoText.isEmpty() ? null : poNoText);
			model.addAttribute("vendorId", vendorIdText.isEmpty() ? null : vendorIdText);
			model.addAttribute("inboundStatus", inboundStatusText.isEmpty() ? null : inboundStatusText);
			model.addAttribute("startDate", startDateText.isEmpty() ? null : startDateText);
			model.addAttribute("endDate", endDateText.isEmpty() ? null : endDateText);
		}

		model.addAttribute("poPage", poPage);
		// 頁碼清單，0 代表「…」；poPage.number 從 0 開始，buildPageNumbers 從 1 開始
		model.addAttribute("pageNumbers", PsiUtils.buildPageNumbers(poPage.getNumber() + 1, poPage.getTotalPages()));
		// 有沒有用查詢條件查：查無資料時決定表格顯示哪一句提示
		model.addAttribute("searching", searching);
		return "admin/psi/receiving/listAllReceiving"; //view
	}

	// 歷史驗收紀錄查詢條件的格式檢查，回傳所有錯誤訊息（沒有錯誤時是空的清單）；傳進來的值都已經去掉前後空白，沒填是空字串
	private List<String> validateReceivingSearch(String poNo, String vendorId, String inboundStatus, String startDate,
			String endDate) {
		List<String> errors = new ArrayList<>();

		// PoService 的 generatePoNo 產生的格式：PO + 日期 8 碼 + 流水號 4 碼；訊息和採購系統的查詢（PoNoController）相同
		if (!poNo.isEmpty() && !poNo.matches("^PO\\d{12}$")) {
			errors.add("採購單編號: 格式為 PO 加上 12 位數字，例如 PO202610090001");
		}

		String vendorIdError = PsiUtils.positiveIdError(vendorId, "供應商編號", VENDOR_ID_MAX);
		if (vendorIdError != null) {
			errors.add(vendorIdError);
		}

		// 已結案的單都驗收過：1 驗收成功、2 驗收失敗、3 已取消；下拉選單的「全部」是空字串，不檢查
		if (!inboundStatus.isEmpty() && !inboundStatus.matches("^[1-3]$")) {
			errors.add("驗收狀態，值不正確");
		}

		// 日期是 input type="date" 送來的 yyyy-MM-dd；起日、迄日都正確時才比較先後
		LocalDate startDateValue = PsiUtils.parseDate(startDate);
		LocalDate endDateValue = PsiUtils.parseDate(endDate);
		if (!startDate.isEmpty() && startDateValue == null) {
			errors.add("驗收日期（起日），格式不正確");
		}
		if (!endDate.isEmpty() && endDateValue == null) {
			errors.add("驗收日期（迄日），格式不正確");
		}
		if (startDateValue != null && endDateValue != null && startDateValue.isAfter(endDateValue)) {
			errors.add("驗收日期，起日不能晚於迄日");
		}
		return errors;
	}

	// 單筆資料：由首頁列表、驗收紀錄的「詳細」連過來，依 poId 顯示採購單與明細
	@GetMapping("/listOneReceiving")
	public String listOneReceiving(@RequestParam("poId") Integer poId, Model model,
			RedirectAttributes redirectAttributes) {
		PoVO poVO = receivingSvc.getOneApprovedPo(poId);

		// 查不到，或不是申請通過、已結案的採購單時回首頁顯示錯誤訊息；update 被擋下帶過來的錯誤訊息繼續帶到首頁顯示，沒有才用預設的訊息
		if (poVO == null) {
			Object errorMessage = model.containsAttribute("errorMessage") ? model.getAttribute("errorMessage")
					: PO_NOT_FOUND_MESSAGE;
			redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
			return "redirect:/admin/psi/receiving";
		}
		model.addAttribute("poVO", poVO);

		return "admin/psi/receiving/listOneReceiving"; //view
	}

	// 驗收頁：由單筆頁的「驗收」按鈕，或驗收紀錄、單筆頁的「修改」按鈕連過來
	// 待驗收的採購單是第一次驗收，數量欄位空白；已結案的採購單是修改驗收紀錄，帶入上次存的內容
	@PostMapping("/getOneForUpdate")
	public String getOneForUpdate(@RequestParam("poId") Integer poId, Model model, HttpSession session,
			RedirectAttributes redirectAttributes) {
		PoVO poVO = receivingSvc.getOneApprovedPo(poId);

		// 查不到，或不是申請通過、已結案的採購單時回首頁顯示錯誤訊息
		if (poVO == null) {
			redirectAttributes.addFlashAttribute("errorMessage", PO_NOT_FOUND_MESSAGE);
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

		// 3. 檢查每筆明細的數量：輸入的不是整數時，Spring 在綁定階段就已經記下錯誤；
		//    其餘欄位照樣交給 ReceivingService 的 checkReceiving 檢查空白、範圍，兩邊的錯誤一起顯示
		//    綁定失敗的欄位值是 null，交給 checkReceiving 時跳過，同一格才不會同時出現兩種訊息
		List<String> detailErrors = getDetailBindingErrors(result, poVO);
		detailErrors.addAll(receivingSvc.checkReceiving(poVO, getTypeMismatchFields(result)));

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
			modified = productPurchaseFlow.receive(poVO, inboundEmployee, receivedContent);
		} catch (IllegalArgumentException e) {
			return redirectWithError(poVO.getPoId(), e.getMessage(), redirectAttributes);
		} catch (DataIntegrityViolationException e) {
			// 資料庫的限制擋下這次驗收；整筆已回滾，回單筆頁顯示而不是出現錯誤頁
			return redirectWithError(poVO.getPoId(), "資料庫拒絕這次驗收，請重新進入驗收頁再試一次", redirectAttributes);
		}

		redirectAttributes.addFlashAttribute("success", modified ? "驗收紀錄已修改" : "驗收完成");
		return redirectToListOneReceiving(poVO.getPoId());
	}

	// 到貨數量、不良品數量在綁定階段的錯誤（輸入的不是整數），整理成「第幾筆（商品名稱-規格名稱）：訊息」的文字
	// 訊息取自 messages.properties 的 typeMismatch.poVO.poDetails.arrivedPcs、defectPcs
	// 要在 prepareReceiving 之後呼叫，明細的規格才已經填好；查不到規格時只寫「第幾筆：」
	private List<String> getDetailBindingErrors(BindingResult result, PoVO poVO) {
		List<String> errorMessages = new ArrayList<>();
		for (FieldError fieldError : result.getFieldErrors()) {
			Matcher matcher = DETAIL_QUANTITY_FIELD.matcher(fieldError.getField());
			if (matcher.matches()) {
				int index = Integer.parseInt(matcher.group(1));
				String skuLabel = null;
				if (poVO.getPoDetails() != null && index < poVO.getPoDetails().size()) {
					skuLabel = PsiUtils.skuLabel(poVO.getPoDetails().get(index).getSkuId());
				}
				errorMessages.add("第 " + (index + 1) + " 筆" + (skuLabel == null ? "" : "（" + skuLabel + "）") + "："
						+ messageSource.getMessage(fieldError, LocaleContextHolder.getLocale()));
			}
		}
		return errorMessages;
	}

	// 綁定階段出錯（輸入的不是整數）的到貨數量、不良品數量欄位名，例如 poDetails[0].arrivedPcs
	// 交給 ReceivingService 的 checkReceiving 跳過這些欄位，避免同一格再出現「請勿空白」
	private Set<String> getTypeMismatchFields(BindingResult result) {
		Set<String> typeMismatchFields = new HashSet<>();
		for (FieldError fieldError : result.getFieldErrors()) {
			if (DETAIL_QUANTITY_FIELD.matcher(fieldError.getField()).matches()) {
				typeMismatchFields.add(fieldError.getField());
			}
		}
		return typeMismatchFields;
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
		Integer employeeId = PsiUtils.getLoggedInEmployeeId(session);
		return employeeId == null ? null : receivingSvc.getOneEmployee(employeeId);
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

	// 網址的參數型別不對（例如 poId=abc）或少了必要的參數時，回首頁並顯示錯誤訊息
	// 只處理這支 controller 的方法丟出的例外；例外處理不會經過 receiving 方法，待驗收的採購單要自己再放一次
	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
	public ModelAndView handleError(Exception e) {
		ModelAndView mav = new ModelAndView("admin/psi/receiving/index");
		mav.addObject("errorMessage", "網址的參數不正確，請從進貨系統管理重新操作");
		mav.addObject("poListData", receivingSvc.getPendingInboundPos());
		return mav;
	}

}
