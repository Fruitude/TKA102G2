package com.fruitude.chat.model;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MessageRepository extends JpaRepository<Message, Integer> {

	// 一位會員的對話：指定狀態的訊息，依發送時間由舊到新，同一時間的再依訊息編號
	List<Message> findByMemberIdAndStatusOrderByCreatedAtAscMessageIdAsc(Integer memberId, Integer status);

	// 一位會員最新一則指定發送者、指定狀態、有文字內容的訊息；建議回覆用（CannedReplyService 的 getSuggestions）
	Optional<Message> findFirstByMemberIdAndSenderTypeAndStatusAndMessageContentIsNotNullOrderByCreatedAtDescMessageIdDesc(
			Integer memberId, Integer senderType, Integer status);

	// 客服開啟對話時，把這位會員送來、還沒讀的訊息改成已讀並記下已讀時間，回傳更新的筆數
	// 只記哪一則被讀、什麼時候讀，不記是哪位員工讀的
	// 自己開一個交易，更新完就 commit，之後的 Redis 更新與推播看到的都是已讀後的資料
	@Transactional
	@Modifying
	@Query("update Message m set m.isRead = 1, m.readAt = :readAt "
			+ "where m.memberId = :memberId and m.senderType = 1 and m.isRead = 0")
	int markMemberMessagesRead(@Param("memberId") Integer memberId, @Param("readAt") LocalDateTime readAt);

	// 後台對話清單：每位有顯示中訊息的會員一列
	// 每列依序是 會員編號、會員姓名、最後訊息時間、會員送來的未讀數；最後訊息時間由新到舊
	// Redis 沒有清單資料、或 Redis 連不上時用這個從 MySQL 算
	@Query(value = "select m.member_id, mb.member_name, max(m.created_at), "
			+ "sum(case when m.sender_type = 1 and m.is_read = 0 then 1 else 0 end) "
			+ "from message m left join member mb on mb.member_id = m.member_id "
			+ "where m.status = 1 "
			+ "group by m.member_id, mb.member_name "
			+ "order by max(m.created_at) desc", nativeQuery = true)
	List<Object[]> findConversationRows();

	// 會員姓名：每列依序是 會員編號、會員姓名；查不到的會員不會出現在結果裡
	@Query(value = "select member_id, member_name from member where member_id in (:memberIds)", nativeQuery = true)
	List<Object[]> findMemberNames(@Param("memberIds") Collection<Integer> memberIds);

}
