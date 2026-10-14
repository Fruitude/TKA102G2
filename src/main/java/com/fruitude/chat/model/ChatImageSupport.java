package com.fruitude.chat.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

// 聊天圖片的上傳與讀取規則，照商品圖片的規則（ProductImageService 的 ImageUploadSupport、packed）另寫一份
// 商品那份只能套在 ProductImage 上，所以這裡不直接呼叫；商品的規則改了，這裡要跟著改
public final class ChatImageSupport {

	// 單張圖片上限，和商品圖片相同
	public static final long MAX_SIZE = 5 * 1024 * 1024;

	// 檢查通過的圖片：內容、整理過的檔名、由檔頭判斷出來的類型
	public record UploadedImage(byte[] data, String name, String type) {
	}

	private ChatImageSupport() {
	}

	// 檢查一張上傳的圖片：不可空檔、不可超過 5 MB、檔頭必須是 PNG / JPEG / GIF，而且 ImageIO 讀得出來
	// 類型只看檔頭，不看副檔名和瀏覽器送來的 Content-Type；檔名去掉路徑，控制字元與雙引號換成 _，不可超過 255 字
	// 不符合時丟 IllegalArgumentException，檔案讀取失敗丟 IOException
	public static UploadedImage check(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("請選擇圖片檔案");
		}
		if (file.getSize() > MAX_SIZE) {
			throw new IllegalArgumentException("圖片不可超過 5 MB");
		}
		byte[] data = file.getBytes();
		String type = detectType(data);
		if (type == null) {
			throw new IllegalArgumentException("只接受 PNG、JPEG 或 GIF 圖片");
		}
		if (ImageIO.read(new ByteArrayInputStream(data)) == null) {
			throw new IllegalArgumentException("圖片內容無法讀取");
		}
		String name = file.getOriginalFilename();
		name = name == null ? "image" : name.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "_");
		if (name.length() > 255) {
			throw new IllegalArgumentException("圖片檔名不可超過 255 字");
		}
		return new UploadedImage(data, name, type);
	}

	// 由檔頭判斷圖片類型：PNG、JPEG、GIF 之外回傳 null
	// 讀圖片時也用這個決定 Content-Type，不信任資料庫存的 image_type
	public static String detectType(byte[] data) {
		if (data == null) {
			return null;
		}
		if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
			return "image/png";
		}
		if (data.length >= 3 && data[0] == (byte) 0xff && data[1] == (byte) 0xd8 && data[2] == (byte) 0xff) {
			return "image/jpeg";
		}
		if (data.length >= 6 && new String(data, 0, 6, StandardCharsets.US_ASCII).matches("GIF8[79]a")) {
			return "image/gif";
		}
		return null;
	}

	// 給瀏覽器比對快取用的 ETag：圖片內容的 MD5，縮圖另外加上 -thumb100，和原圖區分
	public static String etag(byte[] data, boolean thumbnail) {
		return "\"" + DigestUtils.md5DigestAsHex(data) + (thumbnail ? "-thumb100" : "") + "\"";
	}

}
