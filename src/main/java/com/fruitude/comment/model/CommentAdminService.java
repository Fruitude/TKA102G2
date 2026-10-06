package com.fruitude.comment.model;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 評論管理服務：查詢既有訂單評論、統計狀態，並處理遭檢舉評論的保留或隱藏。
 */
@Service
@Transactional(readOnly = true)
public class CommentAdminService {

	public static final int STATUS_VISIBLE = 1;
	public static final int STATUS_REPORTED = 2;
	public static final int STATUS_HIDDEN = 3;

	private static final String COMMENT_FROM =
			" FROM orders_detail detail "
			+ "JOIN orders orders_data ON orders_data.orders_id = detail.orders_id "
			+ "LEFT JOIN member member_data ON member_data.member_id = orders_data.member_id ";

	private final NamedParameterJdbcTemplate jdbc;

	public CommentAdminService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 依關鍵字、狀態、星等與日期分頁查詢評論，回傳頁面需要的統計資料。 */
	public Map<String, Object> search(String keyword, Integer status, Integer rating,
			LocalDate startDate, LocalDate endDate, int page, int size) {
		validateSearch(status, rating, startDate, endDate, page, size);
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		String where = buildWhere(keyword, status, rating, startDate, endDate, parameters);

		long totalElements = jdbc.queryForObject(
				"SELECT COUNT(*)" + COMMENT_FROM + where, parameters, Long.class);
		int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
		parameters.addValue("limit", size);
		parameters.addValue("offset", page * size);

		String query = "SELECT detail.orders_detail_id, detail.orders_id, detail.sku_id, "
				+ "detail.product_name, orders_data.member_id, member_data.member_name, "
				+ "member_data.member_account, detail.comment_text, detail.comment_date, "
				+ "detail.comment_status, detail.comment_star "
				+ COMMENT_FROM + where
				+ " ORDER BY CASE WHEN detail.comment_status = 2 THEN 0 ELSE 1 END, "
				+ "detail.comment_date DESC, detail.orders_detail_id DESC LIMIT :limit OFFSET :offset";
		List<CommentAdminRow> comments = jdbc.query(query, parameters, (result, rowNumber) ->
				new CommentAdminRow(
						(Integer) result.getObject("orders_detail_id"),
						(Integer) result.getObject("orders_id"),
						(Integer) result.getObject("sku_id"),
						result.getString("product_name"),
						(Integer) result.getObject("member_id"),
						result.getString("member_name"),
						result.getString("member_account"),
						result.getString("comment_text"),
						toLocalDateTime(result.getTimestamp("comment_date")),
						(Integer) result.getObject("comment_status"),
						(Integer) result.getObject("comment_star")));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("comments", comments);
		body.put("page", page);
		body.put("size", size);
		body.put("totalElements", totalElements);
		body.put("totalPages", totalPages);
		body.put("first", page == 0);
		body.put("last", totalPages == 0 || page >= totalPages - 1);
		body.put("summary", loadSummary());
		return body;
	}

	/** 待處理檢舉只能判定為保留或隱藏，不直接刪除評論與訂單明細。 */
	@Transactional
	public CommentAdminRow moderate(Integer ordersDetailId, String action) {
		if (ordersDetailId == null) throw new IllegalArgumentException("評論編號不可空白");
		String cleanAction = action == null ? "" : action.trim().toUpperCase();
		int nextStatus;
		if ("KEEP".equals(cleanAction)) {
			nextStatus = STATUS_VISIBLE;
		} else if ("HIDE".equals(cleanAction)) {
			nextStatus = STATUS_HIDDEN;
		} else {
			throw new IllegalArgumentException("檢舉處理動作不正確");
		}

		int updated = jdbc.update("UPDATE orders_detail SET comment_status = :nextStatus "
				+ "WHERE orders_detail_id = :id AND comment_status = :reportedStatus",
				new MapSqlParameterSource()
						.addValue("nextStatus", nextStatus)
						.addValue("id", ordersDetailId)
						.addValue("reportedStatus", STATUS_REPORTED));
		if (updated == 0) {
			if (!commentExists(ordersDetailId)) throw new NoSuchElementException("找不到評論");
			throw new IllegalArgumentException("這筆檢舉已處理，請重新整理清單");
		}
		return findOne(ordersDetailId);
	}

	/** 讀取單筆評論，供處理完成後回傳最新狀態。 */
	public CommentAdminRow findOne(Integer ordersDetailId) {
		String query = "SELECT detail.orders_detail_id, detail.orders_id, detail.sku_id, "
				+ "detail.product_name, orders_data.member_id, member_data.member_name, "
				+ "member_data.member_account, detail.comment_text, detail.comment_date, "
				+ "detail.comment_status, detail.comment_star " + COMMENT_FROM
				+ "WHERE detail.orders_detail_id = :id";
		List<CommentAdminRow> rows = jdbc.query(query,
				new MapSqlParameterSource("id", ordersDetailId), (result, rowNumber) ->
						new CommentAdminRow(
								(Integer) result.getObject("orders_detail_id"),
								(Integer) result.getObject("orders_id"),
								(Integer) result.getObject("sku_id"), result.getString("product_name"),
								(Integer) result.getObject("member_id"), result.getString("member_name"),
								result.getString("member_account"), result.getString("comment_text"),
								toLocalDateTime(result.getTimestamp("comment_date")),
								(Integer) result.getObject("comment_status"),
								(Integer) result.getObject("comment_star")));
		if (rows.isEmpty()) throw new NoSuchElementException("找不到評論");
		return rows.get(0);
	}

	private String buildWhere(String keyword, Integer status, Integer rating,
			LocalDate startDate, LocalDate endDate, MapSqlParameterSource parameters) {
		StringBuilder where = new StringBuilder(" WHERE detail.comment_text IS NOT NULL "
				+ "AND TRIM(detail.comment_text) <> '' AND detail.comment_status IN (1, 2, 3) ");
		String cleanKeyword = keyword == null ? "" : keyword.trim();
		if (!cleanKeyword.isEmpty()) {
			where.append("AND (LOWER(detail.product_name) LIKE LOWER(CONCAT('%', :keyword, '%')) ")
					.append("OR LOWER(detail.comment_text) LIKE LOWER(CONCAT('%', :keyword, '%')) ")
					.append("OR LOWER(member_data.member_name) LIKE LOWER(CONCAT('%', :keyword, '%')) ")
					.append("OR LOWER(member_data.member_account) LIKE LOWER(CONCAT('%', :keyword, '%')) ")
					.append("OR CAST(detail.orders_id AS CHAR) LIKE CONCAT('%', :keyword, '%')) ");
			parameters.addValue("keyword", cleanKeyword);
		}
		if (status != null) {
			where.append("AND detail.comment_status = :status ");
			parameters.addValue("status", status);
		}
		if (rating != null) {
			where.append("AND detail.comment_star = :rating ");
			parameters.addValue("rating", rating);
		}
		if (startDate != null) {
			where.append("AND detail.comment_date >= :startAt ");
			parameters.addValue("startAt", startDate.atStartOfDay());
		}
		if (endDate != null) {
			where.append("AND detail.comment_date < :endAt ");
			parameters.addValue("endAt", endDate.plusDays(1).atStartOfDay());
		}
		return where.toString();
	}

	private Map<String, Object> loadSummary() {
		Map<String, Object> values = jdbc.queryForMap(
				"SELECT COUNT(*) AS total_count, "
				+ "COALESCE(SUM(comment_status = 1), 0) AS visible_count, "
				+ "COALESCE(SUM(comment_status = 2), 0) AS reported_count, "
				+ "COALESCE(SUM(comment_status = 3), 0) AS hidden_count "
				+ "FROM orders_detail WHERE comment_text IS NOT NULL AND TRIM(comment_text) <> '' "
				+ "AND comment_status IN (1, 2, 3)", new MapSqlParameterSource());
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("total", number(values.get("total_count")));
		summary.put("visible", number(values.get("visible_count")));
		summary.put("reported", number(values.get("reported_count")));
		summary.put("hidden", number(values.get("hidden_count")));
		return summary;
	}

	private void validateSearch(Integer status, Integer rating, LocalDate startDate, LocalDate endDate,
			int page, int size) {
		if (status != null && status != STATUS_VISIBLE && status != STATUS_REPORTED && status != STATUS_HIDDEN) {
			throw new IllegalArgumentException("評論狀態不正確");
		}
		if (rating != null && (rating < 1 || rating > 5)) throw new IllegalArgumentException("星等必須介於 1 到 5");
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("開始日期不可晚於結束日期");
		}
		if (page < 0) throw new IllegalArgumentException("頁碼不可小於 0");
		if (size < 1 || size > 100) throw new IllegalArgumentException("每頁筆數必須介於 1 到 100");
	}

	private boolean commentExists(Integer ordersDetailId) {
		Long count = jdbc.queryForObject("SELECT COUNT(*) FROM orders_detail WHERE orders_detail_id = :id",
				new MapSqlParameterSource("id", ordersDetailId), Long.class);
		return count != null && count > 0;
	}

	private long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0L; }
	private LocalDateTime toLocalDateTime(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
}
