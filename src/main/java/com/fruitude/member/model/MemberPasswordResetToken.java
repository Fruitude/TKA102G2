package com.fruitude.member.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 會員密碼重設憑證：資料庫只保存 Token 的 SHA-256 雜湊，不保存 Email 內的原始 Token。
 */
@Entity
@Table(name = "member_password_reset_token", indexes = {
		@Index(name = "idx_password_reset_member_created", columnList = "member_id, created_at"),
		@Index(name = "idx_password_reset_expires", columnList = "expires_at")
})
public class MemberPasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reset_token_id")
	private Long resetTokenId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_password_reset_member"))
	private MemberVO member;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "used_at")
	private LocalDateTime usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	// 樂觀鎖可防止同一個一次性 Token 被兩個同時送出的請求重複使用。
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion;

	/** 新增資料時由系統記錄建立時間。 */
	@PrePersist
	public void applyCreatedAt() {
		if (createdAt == null) createdAt = LocalDateTime.now();
	}

	public Long getResetTokenId() { return resetTokenId; }
	public void setResetTokenId(Long resetTokenId) { this.resetTokenId = resetTokenId; }
	public MemberVO getMember() { return member; }
	public void setMember(MemberVO member) { this.member = member; }
	public String getTokenHash() { return tokenHash; }
	public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
	public LocalDateTime getExpiresAt() { return expiresAt; }
	public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
	public LocalDateTime getUsedAt() { return usedAt; }
	public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public Long getRowVersion() { return rowVersion; }
	public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
}
