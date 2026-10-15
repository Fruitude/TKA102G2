package com.fruitude.utils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import jakarta.servlet.http.HttpSession;

// 進銷存（psi）共用的小工具，全部是 static 方法：
// 查詢條件的格式檢查（positiveIdError、parseDate）、省略號頁碼（buildPageNumbers）、登入員工編號（getLoggedInEmployeeId）
public class PsiUtils {

	// 分頁頁碼：頭尾各顯示幾頁、目前這頁的前後各顯示幾頁（buildPageNumbers）
	private static final int PAGE_EDGE_COUNT = 5;
	private static final int PAGE_AROUND_COUNT = 2;

	// 編號欄位的檢查：text 已經去掉前後空白，沒填（空字串）不檢查
	// 要是正整數；max 不是 null 時也不能大於 max。通過回傳 null，不通過回傳錯誤訊息（開頭是 label）
	// max 是 null 時不檢查上限，數字太大（超過 int）由呼叫端當成查無資料
	public static String positiveIdError(String text, String label, Integer max) {
		if (text.isEmpty()) {
			return null;
		}
		if (!text.matches("^\\d+$") || text.matches("^0+$")) {
			// 英文字、符號、負號、小數點都不是數字；0 不是正整數
			return label + "，只能輸入正整數";
		}
		if (max != null) {
			// 先去掉開頭的 0 再比位數，數字太大時不用轉成 int（會超過 int 的範圍）
			String digits = text.replaceFirst("^0+", "");
			if (digits.length() > String.valueOf(max).length() || Integer.parseInt(digits) > max) {
				return label + ": 不能大於" + max;
			}
		}
		return null;
	}

	// input type="date" 送來的 yyyy-MM-dd 轉成 LocalDate；空字串或格式不正確（例如 2026-13-01）時回傳 null
	public static LocalDate parseDate(String text) {
		if (text.isEmpty()) {
			return null;
		}
		try {
			return LocalDate.parse(text);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	// 分頁要顯示的頁碼：頭尾各 PAGE_EDGE_COUNT 頁、加上目前這頁的前後各 PAGE_AROUND_COUNT 頁，中間跳過的部分放 0，畫面顯示成「…」
	// currentPage、totalPages 都從 1 開始算；只跳過一頁時直接顯示那一頁，不放「…」
	// 例：共 60 頁、目前第 30 頁 → 1 2 3 4 5 0 28 29 30 31 32 0 56 57 58 59 60；總頁數不多時全部列出
	public static List<Integer> buildPageNumbers(int currentPage, int totalPages) {
		TreeSet<Integer> shownPages = new TreeSet<>();
		for (int i = 1; i <= PAGE_EDGE_COUNT; i++) {
			shownPages.add(i);
			shownPages.add(totalPages - PAGE_EDGE_COUNT + i);
		}
		for (int i = currentPage - PAGE_AROUND_COUNT; i <= currentPage + PAGE_AROUND_COUNT; i++) {
			shownPages.add(i);
		}

		List<Integer> pageNumbers = new ArrayList<>();
		int previousPage = 0;
		for (int pageNumber : shownPages) {
			if (pageNumber < 1 || pageNumber > totalPages) {
				continue;
			}
			if (pageNumber - previousPage == 2) {
				pageNumbers.add(previousPage + 1);
			} else if (pageNumber - previousPage > 2) {
				pageNumbers.add(0);
			}
			pageNumbers.add(pageNumber);
			previousPage = pageNumber;
		}
		return pageNumbers;
	}

	// 目前登入後台的員工編號（EmployeeSessionController 登入時放進 session 的 loggedInEmployeeId）；沒登入或登入逾時回傳 null
	// 只讀 session，不查資料庫；要員工資料時由呼叫端用自己的 service 查
	public static Integer getLoggedInEmployeeId(HttpSession session) {
		Object employeeId = session.getAttribute("loggedInEmployeeId");
		return employeeId instanceof Number ? ((Number) employeeId).intValue() : null;
	}
}
