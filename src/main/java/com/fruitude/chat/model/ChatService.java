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

	// 員工頻道；會員使用 memberTopic，訂閱權限由 ChatWebSocketConfig 檢查。
	public static final String STAFF_TOPIC = "/topic/chat/staff";
    public static String memberTopic(Integer id) { return "/topic/chat/member/" + id; }
    public record MessagePage(List<ChatMessageView> messages, boolean hasMore, long unreadCount) {}
    // These two records are exclusively for CHAT_VIEW-authorized staff. Member payloads remain ChatMessageView.
    public record StaffMessageView(Integer messageId,Integer memberId,Integer senderType,Integer messageType,
        String messageContent,Integer isRead,LocalDateTime createdAt,List<Integer> imageIds,Integer employeeId,String employeeName,Integer replyMessageId,ChatMessageView.ReplyView reply) {
        public ChatMessageView publicView(){return new ChatMessageView(messageId,memberId,senderType,messageType,messageContent,isRead,createdAt,imageIds,replyMessageId,reply);}
        static StaffMessageView of(ChatMessageView m,Integer id,String name){return new StaffMessageView(m.messageId(),m.memberId(),m.senderType(),m.messageType(),m.messageContent(),m.isRead(),m.createdAt(),m.imageIds(),id,name,m.replyMessageId(),m.reply());}
    }
    public record StaffEvent(String type,Integer memberId,StaffMessageView message,Integer readThroughId) {}
    private Map<Integer,String> employeeNames(List<Integer> ids){
        Map<Integer,String> names=new HashMap<>();
        if(!ids.isEmpty())for(Object[] row:messageRepository.findEmployeeNames(ids))names.put(((Number)row[0]).intValue(),(String)row[1]);
        return names;
    }
    public List<StaffMessageView> getStaffConversation(Integer memberId){
        var messages=messageRepository.findByMemberIdAndStatusOrderByCreatedAtAscMessageIdAsc(memberId,Message.STATUS_SHOWN);
        var names=employeeNames(messages.stream().filter(m->m.getSenderType()==Message.SENDER_EMPLOYEE).map(Message::getEmployeeId).filter(java.util.Objects::nonNull).distinct().toList());
        var base=views(messages);var result=new ArrayList<StaffMessageView>();
        for(int i=0;i<messages.size();i++){var m=messages.get(i);Integer id=m.getSenderType()==Message.SENDER_EMPLOYEE?m.getEmployeeId():null;result.add(StaffMessageView.of(base.get(i),id,names.get(id)));}
        return result;
    }


    public MessagePage getMemberConversation(Integer id,Integer before,Integer after) {
        if((before!=null && before<1)||(after!=null && after<0)||(before!=null && after!=null))
            throw new IllegalArgumentException("無效的訊息分頁");
        var limit=org.springframework.data.domain.PageRequest.of(0,51);
        var rows=after==null?messageRepository.page(id,before,null,limit):messageRepository.after(id,after,limit);
        boolean more=rows.size()>50;
        var selected=new ArrayList<>(rows.subList(0,Math.min(50,rows.size())));
        selected.sort(java.util.Comparator.comparing(Message::getMessageId));
        return new MessagePage(views(selected),more,messageRepository.unreadByMember(id));
    }
    public long memberUnread(Integer id) { return messageRepository.unreadByMember(id); }
    /** Only visible unread member messages count, across all conversations. Redis failures use MySQL. */
    public long staffUnreadCount() {
        if(!cacheUncertain.get())try {
            var cached=chatRedisService.readConversations();
            if(cached!=null)return cached.stream().mapToLong(ChatRedisService.ConversationEntry::unreadCount).sum();
        }catch(DataAccessException unavailable){}
        return getConversationList().stream().mapToLong(ChatConversationView::unreadCount).sum();
    }

    public void markReadByMember(Integer id,Integer through) {
        if(through==null || through<1)throw new IllegalArgumentException("無效的已讀訊息");
        if(messageRepository.markStaffMessagesRead(id,through,LocalDateTime.now())>0)
            publish(new ChatEvent(ChatEvent.MEMBER_READ,id,null,through));
    }
    public ChatImage getMemberImage(Integer id,Integer imageId,boolean thumbnail)throws IOException {
        if(messageImageRepository.owned(imageId,id)==0)return null;
        return getImage(imageId,thumbnail);
    }

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
    private final java.util.concurrent.atomic.AtomicBoolean cacheUncertain=new java.util.concurrent.atomic.AtomicBoolean();

	// 後台對話清單，最後訊息時間由新到舊
	// 先讀 Redis；Redis 沒有清單時由 MySQL 算出來並寫回 Redis；Redis 連不上時直接用 MySQL 算的結果
	public List<ChatConversationView> getConversationList() {
		try {
			if(cacheUncertain.getAndSet(false))try{chatRedisService.clearUnread(null);}catch(DataAccessException error){cacheUncertain.set(true);throw error;}
            List<ChatRedisService.ConversationEntry> entries = chatRedisService.readConversations();
			if (entries != null) {
				return withMemberNames(entries);
			}
			String generation=chatRedisService.generation();
			List<ChatConversationView> conversations = loadConversationsFromDatabase();
			chatRedisService.rebuild(conversations,generation);
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
        return views(messages);
    }

	// 對話列預覽：只取會員送來、客服還沒讀的訊息，由舊到新；只讀資料，不標已讀、不推播
	public List<ChatMessageView> getUnreadPreview(Integer memberId) {
		return getConversation(memberId).stream()
				.filter(message -> Integer.valueOf(Message.SENDER_MEMBER).equals(message.senderType())
						&& Integer.valueOf(Message.UNREAD).equals(message.isRead()))
				.toList();
	}

    private List<ChatMessageView> views(List<Message> messages) {
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

        var replyIds=messages.stream().map(Message::getReplyMessageId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Integer,Message> originals=new HashMap<>();
        if(!replyIds.isEmpty())for(var original:messageRepository.findAllById(replyIds))originals.put(original.getMessageId(),original);
		List<ChatMessageView> messageViews = new ArrayList<>();
		for (Message message : messages) {
			var view=ChatMessageView.from(message,imageIdsByMessageId.get(message.getMessageId()));
            if(message.getReplyMessageId()!=null){
                var target=originals.get(message.getReplyMessageId());
                view=view.withReply(target!=null && target.getMemberId().equals(message.getMemberId()) && Integer.valueOf(Message.STATUS_SHOWN).equals(target.getStatus())
                    ?ChatMessageView.preview(target):new ChatMessageView.ReplyView(message.getReplyMessageId(),null,"原訊息已無法顯示"));
            }
            messageViews.add(view);
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
		Integer through=messageRepository.lastMemberMessage(memberId);
        if (through==null || messageRepository.markMemberMessagesReadThrough(memberId,through,LocalDateTime.now()) == 0) {
			return;
		}
		try {
			chatRedisService.clearUnread(memberId);
		} catch (DataAccessException e) {
			cacheUncertain.set(true);
            System.out.println("聊天室更新 Redis 未讀數失敗：" + e.getMessage());
		}
		publish(new ChatEvent(ChatEvent.READ, memberId, null,through));
	}

	// 客服送出訊息：文字與圖片至少一個，圖片最多 MAX_IMAGES 張，每張依 ChatImageSupport 的規則檢查
	// employeeId 是送出訊息的員工（session 的登入員工），只存進資料庫，不會出現在頁面或推播裡
	// message_type：有圖片時是 1（圖片，文字可有可無）；沒有圖片、但文字是由建議回覆帶入（cannedId 有值）時是 2（罐頭訊息，
	// 客服改過內容也一樣）；其他是 0（文字）
	// 任何一項檢查沒過丟 IllegalArgumentException（圖片讀取失敗丟 IOException），不會新增任何資料
	public ChatMessageView sendStaffMessage(Integer memberId, Integer employeeId, String content,
			List<MultipartFile> images, Integer cannedId) throws IOException {
        return sendStaffMessage(memberId,employeeId,content,images,cannedId,null);
    }
    public ChatMessageView sendStaffMessage(Integer memberId,Integer employeeId,String content,List<MultipartFile> images,Integer cannedId,Integer replyMessageId)throws IOException {
		if (employeeId == null) {
			throw new IllegalArgumentException("登入逾時，請重新登入");
		}
		if (cannedId != null && !cannedReplyService.exists(cannedId)) {
			throw new IllegalArgumentException("查無此罐頭訊息，請重新選擇");
		}
        return send(memberId,employeeId,Message.SENDER_EMPLOYEE,content,images,cannedId,replyMessageId);
    }
    public ChatMessageView sendMemberMessage(Integer memberId,String content,List<MultipartFile> images)throws IOException {
        return sendMemberMessage(memberId,content,images,null);
    }
    public ChatMessageView sendMemberMessage(Integer memberId,String content,List<MultipartFile> images,Integer replyMessageId)throws IOException {
        return send(memberId,null,Message.SENDER_MEMBER,content,images,null,replyMessageId);
    }
    private ChatMessageView send(Integer memberId,Integer employeeId,int sender,String content,
            List<MultipartFile> images,Integer cannedId,Integer replyMessageId)throws IOException {
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
		message.setSenderType(sender);
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
        Message[] replyTarget={null};
		Message savedMessage = transactionTemplate.execute(status -> {
            if(replyMessageId!=null){
                if(replyMessageId<1)throw new IllegalArgumentException("無效的回覆訊息");
                var target=messageRepository.findById(replyMessageId).orElseThrow(()->new IllegalArgumentException("原訊息已無法回覆"));
                if(!memberId.equals(target.getMemberId()) || !Integer.valueOf(Message.STATUS_SHOWN).equals(target.getStatus()))
                    throw new IllegalArgumentException("原訊息已無法回覆");
                replyTarget[0]=target;message.setReplyMessageId(replyMessageId);
            }
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
			cacheUncertain.set(true);
            System.out.println("聊天室更新 Redis 清單失敗：" + e.getMessage());
		}
		ChatMessageView messageView = ChatMessageView.from(savedMessage, imageIds);
        if(replyTarget[0]!=null)messageView=messageView.withReply(ChatMessageView.preview(replyTarget[0]));
		publish(new ChatEvent(ChatEvent.MESSAGE, memberId, messageView),employeeId);
		return messageView;
	}

    private void publish(ChatEvent event) {publish(event,null);}
    private void publish(ChatEvent event,Integer employeeId) {
        Object staffPayload=event;
        if(event.message()!=null){
            String name=null;
            if(employeeId!=null)try{name=employeeNames(List.of(employeeId)).get(employeeId);}
            catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("客服員工姓名讀取失敗，保留員工編號",unavailable);}
            staffPayload=new StaffEvent(event.type(),event.memberId(),StaffMessageView.of(event.message(),employeeId,name),event.readThroughId());
        }
        // Member messages deliberately contain no employee ID/name. A delivery failure cannot undo a commit.
        for(String topic:List.of(STAFF_TOPIC,memberTopic(event.memberId())))try {
            messagingTemplate.convertAndSend(topic,topic.equals(STAFF_TOPIC)?staffPayload:event);
        }catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("聊天室推播中斷，重連後補載",error);}
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
        if(entries.isEmpty())return List.of();
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
