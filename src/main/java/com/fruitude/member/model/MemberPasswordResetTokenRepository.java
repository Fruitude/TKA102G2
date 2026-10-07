package com.fruitude.member.model;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 密碼重設憑證資料存取層。 */
@Repository
public interface MemberPasswordResetTokenRepository extends JpaRepository<MemberPasswordResetToken, Long> {

	Optional<MemberPasswordResetToken> findByTokenHash(String tokenHash);

	// 同一會員一分鐘內只寄一次，避免重複點擊或惡意請求大量寄信。
	boolean existsByMemberMemberIdAndCreatedAtAfter(Integer memberId, LocalDateTime createdAfter);

	/** 產生新連結時，讓同一會員之前尚未使用的連結立即失效。 */
	@Modifying
	@Query("UPDATE MemberPasswordResetToken token SET token.usedAt = :now "
			+ "WHERE token.member.memberId = :memberId AND token.usedAt IS NULL")
	int invalidateUnusedTokens(@Param("memberId") Integer memberId, @Param("now") LocalDateTime now);
}
