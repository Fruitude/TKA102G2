package com.fruitude.chat.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

// 上傳直接共用商品 ImageUploadSupport；聊天的讀取類型判斷與 ETag 留在此處。
public final class ChatImageSupport {

	// 單張圖片上限，和商品圖片相同
	public static final long MAX_SIZE = com.fruitude.product.model.ProductImageService.ImageUploadSupport.MAX_SIZE;

	// 檢查通過的圖片：內容、整理過的檔名、由檔頭判斷出來的類型
	public record UploadedImage(byte[] data, String name, String type) {
	}

	private ChatImageSupport() {
	}

	// 檢查一張上傳的圖片：不可空檔、不可超過 5 MB、檔頭必須是 PNG / JPEG / GIF，而且 ImageIO 讀得出來
	// 類型只看檔頭，不看副檔名和瀏覽器送來的 Content-Type；檔名去掉路徑，控制字元與雙引號換成 _，不可超過 255 字
	// 不符合時丟 IllegalArgumentException，檔案讀取失敗丟 IOException
    public static UploadedImage check(MultipartFile file) throws IOException {
        var image=new com.fruitude.product.model.ProductImage();
        com.fruitude.product.model.ProductImageService.ImageUploadSupport.apply(image,file);
        return new UploadedImage(image.getImageData(),image.getImageName(),image.getImageType());
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
