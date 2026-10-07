package com.fruitude.employee.model;

import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 後台操作紀錄資料存取：依指定模組清單與條件組合查詢，僅限員工與權限相關模組。 */
public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLog, Integer> {

	@Query("SELECT log FROM OperationAuditLog log WHERE "
			+ "(:keyword = '' OR LOWER(log.employeeName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(log.targetDisplay) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(log.detailContent) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "AND log.targetModule IN :modules "
			+ "AND (:action = '' OR log.actionType = :action "
			+ "    OR (:action = 'CREATE' AND log.actionType = '新增') "
			+ "    OR (:action = 'UPDATE' AND log.actionType = '修改') "
			+ "    OR (:action = 'STATUS' AND (log.actionType = '狀態變更' OR log.actionType = '啟用' OR log.actionType = '停用')) "
			+ "    OR (:action = 'PASSWORD' AND log.actionType = '重設密碼') "
			+ "    OR (:action = 'ASSIGN' AND (log.actionType = '權限配置' OR log.actionType = '指派'))) "
			+ "AND (:startAt IS NULL OR log.createdAt >= :startAt) "
			+ "AND (:endAt IS NULL OR log.createdAt < :endAt)")
	Page<OperationAuditLog> searchEmployeeAuditLogs(@Param("keyword") String keyword,
			@Param("modules") Collection<String> modules, @Param("action") String action,
			@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt,
			Pageable pageable);
}
