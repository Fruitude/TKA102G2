package com.fruitude.config;

import org.springframework.stereotype.Component;

/**
 * 以伺服器啟動時間當作靜態資源（JS、CSS）的版本號，伺服器每次重啟網址就會跟著改變，
 * 瀏覽器因此會重新下載，不必再手動調整 ?v=數字。樣板用法：@{/js/xxx.js(v=${@appVersion.value})}
 */
@Component("appVersion")
public class AppVersion {

	private final String value = String.valueOf(System.currentTimeMillis());

	public String getValue() {
		return value;
	}
}
