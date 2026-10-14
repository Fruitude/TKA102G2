package com.fruitude.chat.model;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageImageRepository extends JpaRepository<MessageImage, Integer> {

	// 圖片的編號與所屬訊息，不含圖片內容；顯示對話時只需要知道有哪些圖片，圖片本身由頁面另外讀
	interface ImageRef {
		Integer getImageId();

		Integer getMessageId();
	}

	// 多則訊息的圖片編號，依訊息編號、圖片順序排序；只查 ImageRef 的兩個欄位，不會把圖片內容讀出來
	List<ImageRef> findByMessageIdInOrderByMessageIdAscSortOrderAsc(Collection<Integer> messageIds);

}
