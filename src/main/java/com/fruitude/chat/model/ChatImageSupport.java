package com.fruitude.chat.model;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

// 聊天圖片的讀寫：上傳檢查、讀取類型判斷、縮圖、ETag
// 不依賴商品模組，商品圖片規則以後怎麼改都不會影響聊天，反之亦然
public final class ChatImageSupport {

	// 單張圖片上限 5 MB
	public static final long MAX_SIZE = 5 * 1024 * 1024;

	// 縮圖：短邊縮到 240px（後台 100px 方格在 2 倍螢幕上也不用放大），長邊最多 720px（很扁的長圖不會太大）
	public static final int THUMBNAIL_SHORT_SIDE = 240;
	public static final int THUMBNAIL_MAX_LONG_SIDE = 720;
	public static final float THUMBNAIL_JPEG_QUALITY = 0.85f;

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

	// 給瀏覽器比對快取用的 ETag：圖片內容的 MD5，縮圖另外加上 -thumb240，和原圖區分
	// 縮圖規則改變時要換後綴，瀏覽器才會重新抓新縮圖
	public static String etag(byte[] data, boolean thumbnail) {
		return "\"" + DigestUtils.md5DigestAsHex(data) + (thumbnail ? "-thumb240" : "") + "\"";
	}

	// 產生縮圖（JPEG）：讀不出圖片時回傳 null
	// 1. 尺寸：短邊縮到 240px，長邊超過 720px 時改以長邊 720px 為準；原圖已經夠小就不放大
	// 2. 每一步都畫在白底 RGB 上，透明的 PNG / GIF 變白底
	// 3. 大圖先每次縮一半（雙線性），最後一步用雙三次內插縮到剛好，比一次縮很多倍清楚
	// 4. 依 JPEG 的 EXIF 方向標記轉正（手機直拍的照片），在縮好的小圖上轉比較省
	// 5. 用品質 0.85 輸出 JPEG
	public static byte[] thumbnail(byte[] original) throws IOException {
		BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
		if (source == null) {
			return null;
		}
		int width = source.getWidth(), height = source.getHeight();
		double scale = Math.min(1, (double) THUMBNAIL_SHORT_SIDE / Math.min(width, height));
		if (Math.max(width, height) * scale > THUMBNAIL_MAX_LONG_SIDE) {
			scale = (double) THUMBNAIL_MAX_LONG_SIDE / Math.max(width, height);
		}
		int targetWidth = Math.max(1, (int) Math.round(width * scale));
		int targetHeight = Math.max(1, (int) Math.round(height * scale));

		BufferedImage current = source;
		while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
			width /= 2;
			height /= 2;
			current = drawOnWhite(current, width, height, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		current = drawOnWhite(current, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		current = orient(current, jpegOrientation(original));
		return writeJpeg(current);
	}

	// 把圖片縮放畫到指定大小的白底 RGB 圖上
	private static BufferedImage drawOnWhite(BufferedImage image, int width, int height, Object interpolation) {
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var graphics = result.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(image, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	// 依 EXIF 方向（1～8）轉正；1 或無效值不轉。5～8 會交換寬高
	// 每種方向對應的座標轉換：x' = m00·x + m01·y + m02，y' = m10·x + m11·y + m12
	private static BufferedImage orient(BufferedImage image, int orientation) {
		if (orientation < 2 || orientation > 8) {
			return image;
		}
		int width = image.getWidth(), height = image.getHeight();
		AffineTransform transform = switch (orientation) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0); // 左右翻轉
			case 3 -> new AffineTransform(-1, 0, 0, -1, width, height); // 轉 180°
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, height); // 上下翻轉
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // 沿左上到右下的對角線翻轉
			case 6 -> new AffineTransform(0, 1, -1, 0, height, 0); // 順時針轉 90°
			case 7 -> new AffineTransform(0, -1, -1, 0, height, width); // 沿右上到左下的對角線翻轉
			default -> new AffineTransform(0, -1, 1, 0, 0, width); // 8：逆時針轉 90°
		};
		boolean swap = orientation >= 5;
		BufferedImage result = new BufferedImage(swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_RGB);
		var graphics = result.createGraphics();
		try {
			graphics.drawImage(image, transform, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	// 讀 JPEG 的 EXIF 方向標記（IFD0 的 0x0112）；不是 JPEG、沒有 EXIF 或格式不對時回傳 1（不轉）
	static int jpegOrientation(byte[] data) {
		if (data == null || data.length < 4 || (data[0] & 0xff) != 0xff || (data[1] & 0xff) != 0xd8) {
			return 1;
		}
		int position = 2;
		while (position + 4 <= data.length) {
			if ((data[position] & 0xff) != 0xff) {
				return 1;
			}
			int marker = data[position + 1] & 0xff;
			if (marker == 0xff) {
				position++;
				continue;
			}
			// 開始影像資料（SOS）或結束（EOI）之後不會再有 EXIF
			if (marker == 0xda || marker == 0xd9) {
				return 1;
			}
			int length = ((data[position + 2] & 0xff) << 8) | (data[position + 3] & 0xff);
			if (length < 2 || position + 2 + length > data.length) {
				return 1;
			}
			if (marker == 0xe1 && length >= 8 && data[position + 4] == 'E' && data[position + 5] == 'x'
					&& data[position + 6] == 'i' && data[position + 7] == 'f' && data[position + 8] == 0
					&& data[position + 9] == 0) {
				int orientation = exifOrientation(data, position + 10, position + 2 + length);
				if (orientation > 0) {
					return orientation;
				}
			}
			position += 2 + length;
		}
		return 1;
	}

	// 在 TIFF 區塊（start ~ end）裡找 IFD0 的方向標記；找不到回傳 0
	private static int exifOrientation(byte[] data, int start, int end) {
		if (start + 8 > end) {
			return 0;
		}
		boolean little;
		if (data[start] == 'I' && data[start + 1] == 'I') {
			little = true;
		} else if (data[start] == 'M' && data[start + 1] == 'M') {
			little = false;
		} else {
			return 0;
		}
		long directory = start + readUnsigned(data, start + 4, 4, little);
		if (directory + 2 > end) {
			return 0;
		}
		int count = (int) readUnsigned(data, (int) directory, 2, little);
		for (int i = 0; i < count; i++) {
			long entry = directory + 2 + 12L * i;
			if (entry + 12 > end) {
				return 0;
			}
			if (readUnsigned(data, (int) entry, 2, little) == 0x0112) {
				int value = (int) readUnsigned(data, (int) entry + 8, 2, little);
				return value >= 1 && value <= 8 ? value : 0;
			}
		}
		return 0;
	}

	private static long readUnsigned(byte[] data, int offset, int size, boolean little) {
		long value = 0;
		for (int i = 0; i < size; i++) {
			int part = data[offset + (little ? size - 1 - i : i)] & 0xff;
			value = (value << 8) | part;
		}
		return value;
	}

	private static byte[] writeJpeg(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(THUMBNAIL_JPEG_QUALITY);
		var output = new ByteArrayOutputStream();
		try (var stream = ImageIO.createImageOutputStream(output)) {
			writer.setOutput(stream);
			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
		}
		return output.toByteArray();
	}

}
