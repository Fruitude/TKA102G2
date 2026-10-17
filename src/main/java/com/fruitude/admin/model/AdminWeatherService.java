package com.fruitude.admin.model;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

// 後台首頁標題的現在天氣，資料來源：中央氣象署開放資料（https://opendata.cwa.gov.tw）
// 授權碼只從環境變數 CWA_API_KEY 讀取；結果快取在記憶體，避免每次開首頁都呼叫氣象署
@Service
public class AdminWeatherService {

	private static final Logger LOG = LoggerFactory.getLogger(AdminWeatherService.class);
	private static final String BASE_URL = "https://opendata.cwa.gov.tw/api/v1/rest/datastore";
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final Duration CACHE_TTL = Duration.ofMinutes(10);
	private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);
	private static final String MISSING_VALUE = "-99"; // 氣象署以 -99 表示該欄位沒有資料
	private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final RestClient restClient;
	private final String apiKey;
	private final String dataset;
	private final String stationId;

	private CurrentWeather cachedWeather = CurrentWeather.unavailable();
	private Instant nextRefreshAt = Instant.EPOCH;

	public AdminWeatherService(
			@Value("${cwa.api-key:}") String apiKey,
			@Value("${cwa.dataset:O-A0003-001}") String dataset,
			@Value("${cwa.station-id:466920}") String stationId) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(TIMEOUT);
		requestFactory.setReadTimeout(TIMEOUT);
		this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
		this.apiKey = apiKey == null ? "" : apiKey.strip();
		this.dataset = dataset;
		this.stationId = stationId;
	}

	// 快取未過期直接回傳；過期才重抓。失敗時保留上一次的資料，1 分鐘後再試
	public synchronized CurrentWeather findCurrentWeather() {
		if (apiKey.isEmpty()) return CurrentWeather.unavailable();

		Instant now = Instant.now();
		if (now.isBefore(nextRefreshAt)) return cachedWeather;

		try {
			CurrentWeather latest = fetchCurrentWeather();
			if (latest.available()) {
				cachedWeather = latest;
				nextRefreshAt = now.plus(CACHE_TTL);
			} else {
				nextRefreshAt = now.plus(RETRY_AFTER_FAILURE);
			}
		} catch (RuntimeException e) {
			// 不印出例外訊息：其中的請求網址含有授權碼
			LOG.warn("中央氣象署天氣資料取得失敗：{}", e.getClass().getSimpleName());
			nextRefreshAt = now.plus(RETRY_AFTER_FAILURE);
		}
		return cachedWeather;
	}

	private CurrentWeather fetchCurrentWeather() {
		JsonNode body = restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/{dataset}")
						.queryParam("Authorization", apiKey)
						.queryParam("StationId", stationId)
						.queryParam("format", "JSON")
						.build(dataset))
				.retrieve()
				.body(JsonNode.class);
		if (body == null) return CurrentWeather.unavailable();

		JsonNode station = body.path("records").path("Station").path(0);
		JsonNode element = station.path("WeatherElement");
		Double temperature = toDouble(textOf(element.path("AirTemperature")));
		if (temperature == null) return CurrentWeather.unavailable();

		Double humidity = toDouble(textOf(element.path("RelativeHumidity")));
		return new CurrentWeather(true,
				textOf(station.path("StationName")),
				temperature,
				textOf(element.path("Weather")),
				humidity == null ? null : (int) Math.round(humidity),
				toTime(textOf(station.path("ObsTime").path("DateTime"))));
	}

	// 取欄位文字；不存在、空白或 -99 一律視為沒有資料（null）
	private static String textOf(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) return null;
		String text = node.asString().strip();
		return text.isEmpty() || MISSING_VALUE.equals(text) ? null : text;
	}

	private static Double toDouble(String text) {
		if (text == null) return null;
		try {
			double value = Double.parseDouble(text);
			return value <= -99 ? null : value;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// 觀測時間只顯示台北時間的 HH:mm
	private static String toTime(String dateTime) {
		if (dateTime == null) return null;
		try {
			return OffsetDateTime.parse(dateTime).atZoneSameInstant(TAIPEI).format(TIME_FORMAT);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public record CurrentWeather(boolean available, String stationName, Double temperature,
			String weather, Integer humidity, String observedTime) {

		static CurrentWeather unavailable() {
			return new CurrentWeather(false, null, null, null, null, null);
		}
	}
}
