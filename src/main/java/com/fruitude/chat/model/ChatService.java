package com.fruitude.chat.model;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fruitude.product.model.ProductImageService.ProductThumbnailSupport;

// 後台客服聊天室：對話清單、對話內容、已讀、客服送訊（文字與圖片）
// 訊息本體存 MySQL（message 表）；清單排序與未讀數放 Redis（ChatRedisService），Redis 連不上時改查 MySQL，聊天室照常可用
// 新訊息與已讀會推播到 STAFF_TOPIC，開著後台聊天室的員工即時看到
@Service
public class ChatService {

	// 後台聊天室頁面訂閱的推播目的地（ChatWebSocketConfig 只允許訂閱這一個）
	public static final String STAFF_TOPIC = "/topic/chat/staff";

	// 一則訊息最多幾個字
	private static final int MAX_CONTENT_LENGTH = 1000;

	// 一則訊息最多幾張圖片
	public static final int MAX_IMAGES = 5;

	// 讀圖片時回傳給頁面的內容：圖片、Content-Type（只會是 PNG / JPEG / GIF 或 application/octet-stream）、ETag
	public record ChatImage(byte[] data, String type, String etag) {
	}

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private MessageImageRepository messageImageRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private CannedReplyService cannedReplyService;

	@Autowired
	private ChatRedisService chatRedisService;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	// 後台對話清單，最後訊息時間由新到舊
	// 先讀 Redis；Redis 沒有清單時由 MySQL 算出來並寫回 Redis；Redis 連不上時直接用 MySQL 算的結果
	public List<ChatConversationView> getConversationList() {
		try {
			List<ChatRedisService.ConversationEntry> entries = chatRedisService.readConversations();
			if (entries != null) {
				return withMemberNames(entries);
			}
			List<ChatConversationView> conversations = loadConversationsFromDatabase();
			chatRedisService.rebuild(conversations);
			return conversations;
		} catch (DataAccessException e) {
			System.out.println("聊天室讀取 Redis 失敗，改用 MySQL：" + e.getMessage());
			return loadConversationsFromDatabase();
		}
	}

	// 一位會員的對話內容（只有顯示中的訊息），依發送時間由舊到新；不含員工編號
	// 各則訊息的圖片編號一次查出來，不要每則訊息各查一次；只查編號，不讀圖片內容
	public List<ChatMessageView> getConversation(Integer memberId) {
		List<Message> messages = messageRepository.findByMemberIdAndStatusOrderByCreatedAtAscMessageIdAsc(memberId,
				Message.STATUS_SHOWN);
		List<Integer> messageIds = new ArrayList<>();
		for (Message message : messages) {
			messageIds.add(message.getMessageId());
		}
		Map<Integer, List<Integer>> imageIdsByMessageId = new HashMap<>();
		if (!messageIds.isEmpty()) {
			for (MessageImageRepository.ImageRef imageRef : messageImageRepository
					.findByMessageIdInOrderByMessageIdAscSortOrderAsc(messageIds)) {
				imageIdsByMessageId.computeIfAbsent(imageRef.getMessageId(), key -> new ArrayList<>())
						.add(imageRef.getImageId());
			}
		}

		List<ChatMessageView> messageViews = new ArrayList<>();
		for (Message message : messages) {
			messageViews.add(ChatMessageView.from(message, imageIdsByMessageId.get(message.getMessageId())));
		}
		return messageViews;
	}

	// 讀一張聊天圖片；thumbnail 為 true 時縮成最長邊 100px 的 JPEG（沿用商品的 ProductThumbnailSupport）
	// 查不到、沒有圖片內容（例如測試資料）、或縮圖失敗時回傳 null
	// Content-Type 由檔頭判斷，不信任資料庫存的 image_type；不是 PNG / JPEG / GIF 時用 application/octet-stream
	public ChatImage getImage(Integer imageId, boolean thumbnail) throws IOException {
		MessageImage messageImage = messageImageRepository.findById(imageId).orElse(null);
		if (messageImage == null || messageImage.getImageData() == null || messageImage.getImageData().length == 0) {
			return null;
		}
		byte[] data = messageImage.getImageData();
		if (thumbnail) {
			byte[] resized = ProductThumbnailSupport.resize(data);
			return resized == null ? null : new ChatImage(resized, "image/jpeg", ChatImageSupport.etag(data, true));
		}
		String type = ChatImageSupport.detectType(data);
		return new ChatImage(data, type == null ? "application/octet-stream" : type, ChatImageSupport.etag(data, false));
	}

	// 客服開啟對話：這位會員送來的未讀訊息改成已讀，Redis 未讀數歸零，通知其他員工的清單更新
	// 不記錄是哪位員工讀的；沒有未讀訊息時什麼都不做
	public void markReadByStaff(Integer memberId) {
		if (messageRepository.markMemberMessagesRead(memberId, LocalDateTime.now()) == 0) {
			return;
		}
		try {
			chatRedisService.clearUnread(memberId);
		} catch (DataAccessException e) {
			System.out.println("聊天室更新 Redis 未讀數失敗：" + e.getMessage());
		}
		messagingTemplate.convertAndSend(STAFF_TOPIC, new ChatEvent(ChatEvent.READ, memberId, null));
	}

	// 客服送出訊息：文字與圖片至少一個，圖片最多 MAX_IMAGES 張，每張依 ChatImageSupport 的規則檢查
	// employeeId 是送出訊息的員工（session 的登入員工），只存進資料庫，不會出現在頁面或推播裡
	// message_type：有圖片時是 1（圖片，文字可有可無）；沒有圖片、但文字是由建議回覆帶入（cannedId 有值）時是 2（罐頭訊息，
	// 客服改過內容也一樣）；其他是 0（文字）
	// 任何一項檢查沒過丟 IllegalArgumentException（圖片讀取失敗丟 IOException），不會新增任何資料
	public ChatMessageView sendStaffMessage(Integer memberId, Integer employeeId, String content,
			List<MultipartFile> images, Integer cannedId) throws IOException {
		if (employeeId == null) {
			throw new IllegalArgumentException("登入逾時，請重新登入");
		}
		if (cannedId != null && !cannedReplyService.exists(cannedId)) {
			throw new IllegalArgumentException("查無此罐頭訊息，請重新選擇");
		}
		String trimmedContent = content == null ? "" : content.strip();
		if (trimmedContent.length() > MAX_CONTENT_LENGTH) {
			throw new IllegalArgumentException("訊息內容最多 " + MAX_CONTENT_LENGTH + " 字");
		}
		// 瀏覽器沒選檔案時可能送來空的檔案欄位，先排除
		List<MultipartFile> imageFiles = new ArrayList<>();
		if (images != null) {
			for (MultipartFile image : images) {
				if (image != null && !image.isEmpty()) {
					imageFiles.add(image);
				}
			}
		}
		if (trimmedContent.isEmpty() && imageFiles.isEmpty()) {
			throw new IllegalArgumentException("請輸入訊息內容或選擇圖片");
		}
		if (imageFiles.size() > MAX_IMAGES) {
			throw new IllegalArgumentException("一則訊息最多 " + MAX_IMAGES + " 張圖片");
		}
		// 圖片全部先檢查完才開始存，有一張不行就整則不存
		List<ChatImageSupport.UploadedImage> uploadedImages = new ArrayList<>();
		for (int i = 0; i < imageFiles.size(); i++) {
			try {
				uploadedImages.add(ChatImageSupport.check(imageFiles.get(i)));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("第 " + (i + 1) + " 張圖片：" + e.getMessage());
			}
		}
		if (messageRepository.findMemberNames(List.of(memberId)).isEmpty()) {
			throw new IllegalArgumentException("查無此會員");
		}

		LocalDateTime now = LocalDateTime.now();
		Message message = new Message();
		message.setSenderType(Message.SENDER_EMPLOYEE);
		message.setMemberId(memberId);
		message.setEmployeeId(employeeId);
		if (!uploadedImages.isEmpty()) {
			message.setMessageType(Message.TYPE_IMAGE);
		} else if (cannedId != null && !trimmedContent.isEmpty()) {
			message.setMessageType(Message.TYPE_CANNED);
		} else {
			message.setMessageType(Message.TYPE_TEXT);
		}
		message.setMessageContent(trimmedContent.isEmpty() ? null : trimmedContent);
		message.setIsRead(Message.UNREAD);
		message.setStatus(Message.STATUS_SHOWN);
		message.setCreatedAt(now);

		// 訊息與圖片在同一個交易裡存，任何一筆失敗全部還原；execute 回來時已經 commit，之後推播出去別人再查也查得到
		List<Integer> imageIds = new ArrayList<>();
		Message savedMessage = transactionTemplate.execute(status -> {
			Message saved = messageRepository.save(message);
			for (int i = 0; i < uploadedImages.size(); i++) {
				ChatImageSupport.UploadedImage uploadedImage = uploadedImages.get(i);
				MessageImage messageImage = new MessageImage();
				messageImage.setMessageId(saved.getMessageId());
				messageImage.setImageData(uploadedImage.data());
				messageImage.setImageName(uploadedImage.name());
				messageImage.setImageType(uploadedImage.type());
				messageImage.setFileSize(uploadedImage.data().length);
				messageImage.setSortOrder(i + 1);
				messageImage.setCreatedAt(now);
				imageIds.add(messageImageRepository.save(messageImage).getImageId());
			}
			return saved;
		});

		try {
			chatRedisService.touchConversation(memberId, savedMessage.getCreatedAt());
		} catch (DataAccessException e) {
			System.out.println("聊天室更新 Redis 清單失敗：" + e.getMessage());
		}
		ChatMessageView messageView = ChatMessageView.from(savedMessage, imageIds);
		messagingTemplate.convertAndSend(STAFF_TOPIC, new ChatEvent(ChatEvent.MESSAGE, memberId, messageView));
		return messageView;
	}

	// 由 MySQL 算出整份對話清單（MessageRepository 的 findConversationRows）
	private List<ChatConversationView> loadConversationsFromDatabase() {
		List<ChatConversationView> conversations = new ArrayList<>();
		for (Object[] row : messageRepository.findConversationRows()) {
			conversations.add(new ChatConversationView(((Number) row[0]).intValue(), (String) row[1],
					toLocalDateTime(row[2]), row[3] == null ? 0 : ((Number) row[3]).longValue()));
		}
		return conversations;
	}

	// Redis 的清單只有會員編號，姓名一次從 MySQL 查出來補上
	private List<ChatConversationView> withMemberNames(List<ChatRedisService.ConversationEntry> entries) {
		List<Integer> memberIds = new ArrayList<>();
		for (ChatRedisService.ConversationEntry entry : entries) {
			memberIds.add(entry.memberId());
		}
		Map<Integer, String> memberNameByMemberId = new HashMap<>();
		for (Object[] row : messageRepository.findMemberNames(memberIds)) {
			memberNameByMemberId.put(((Number) row[0]).intValue(), (String) row[1]);
		}

		List<ChatConversationView> conversations = new ArrayList<>();
		for (ChatRedisService.ConversationEntry entry : entries) {
			conversations.add(new ChatConversationView(entry.memberId(), memberNameByMemberId.get(entry.memberId()),
					entry.lastMessageAt(), entry.unreadCount()));
		}
		return conversations;
	}

	// native query 的 DATETIME 依 Hibernate 版本可能是 LocalDateTime 或 Timestamp
	private LocalDateTime toLocalDateTime(Object value) {
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime;
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toLocalDateTime();
		}
		return null;
	}

}
