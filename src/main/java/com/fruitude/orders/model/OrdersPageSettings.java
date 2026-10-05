package com.fruitude.orders.model;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 後台訂單管理的「每頁筆數」設定。
 * 預設值可以在 application.properties 用 fruitude.admin.orders.page-size 覆蓋（沒設定就是 50）；
 * 執行中可由 OrdersPageSettingsApiController 讀取、修改。
 * 目前只放在記憶體，伺服器重啟會回到預設值；將來要永久保存，改這個類別的存取方式即可（改存資料庫）。
 */
@Component
public class OrdersPageSettings {

	public static final int MIN_PAGE_SIZE = 1;
	public static final int MAX_PAGE_SIZE = 200;
	public static final int DEFAULT_PAGE_SIZE = 50;

	private final AtomicInteger pageSize;

	public OrdersPageSettings(@Value("${fruitude.admin.orders.page-size:" + DEFAULT_PAGE_SIZE + "}") int initialPageSize) {
		this.pageSize = new AtomicInteger(isValid(initialPageSize) ? initialPageSize : DEFAULT_PAGE_SIZE);
	}

	public int getPageSize() {
		return pageSize.get();
	}

	/** 不在允許範圍內就丟 IllegalArgumentException，不會改動目前的值 */
	public void setPageSize(int newPageSize) {
		if (!isValid(newPageSize)) {
			throw new IllegalArgumentException(
					"每頁筆數必須介於 " + MIN_PAGE_SIZE + " 到 " + MAX_PAGE_SIZE + " 之間");
		}
		pageSize.set(newPageSize);
	}

	public static boolean isValid(int value) {
		return value >= MIN_PAGE_SIZE && value <= MAX_PAGE_SIZE;
	}
}
