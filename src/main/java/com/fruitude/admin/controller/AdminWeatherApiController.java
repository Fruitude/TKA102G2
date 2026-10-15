package com.fruitude.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fruitude.admin.model.AdminWeatherService;
import com.fruitude.admin.model.AdminWeatherService.CurrentWeather;

/**
 * 後台首頁標題的現在天氣 API。
 *
 * GET /api/admin/weather → {"available":true,"stationName":"臺北","temperature":24.0,"weather":"晴","humidity":78,"observedTime":"05:00"}
 * 沒有設定授權碼或取得失敗且沒有快取時，available 為 false，其他欄位為 null。
 * 登入檢查由 AdminAccessInterceptor 處理（/api/admin/**，未登入回 401）。
 */
@RestController
@RequestMapping("/api/admin/weather")
public class AdminWeatherApiController {

	@Autowired
	private AdminWeatherService weatherService;

	@GetMapping
	public CurrentWeather currentWeather() {
		return weatherService.findCurrentWeather();
	}
}
