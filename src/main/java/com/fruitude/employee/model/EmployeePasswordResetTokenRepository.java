package com.fruitude.employee.model;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 員工密碼重設憑證的資料存取層。 */
@Repository
public interface EmployeePasswordResetTokenRepository extends JpaRepository<EmployeePasswordResetToken, Long> {

	Optional<EmployeePasswordResetToken> findByTokenHash(String tokenHash);

	/** 限制同一員工一分鐘內只能寄送一次，避免重複點擊造成大量信件。 */
	boolean existsByEmployeeEmployeeIdAndCreatedAtAfter(Integer employeeId, LocalDateTime createdAfter);

	/** 產生新連結時，讓同一員工先前尚未使用的連結立即失效。 */
	@Modifying
	@Query("UPDATE EmployeePasswordResetToken token SET token.usedAt = :now "
			+ "WHERE token.employee.employeeId = :employeeId AND token.usedAt IS NULL")
	int invalidateUnusedTokens(@Param("employeeId") Integer employeeId, @Param("now") LocalDateTime now);
}
