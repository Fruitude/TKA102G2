package com.fruitude.product.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;

import org.springframework.web.multipart.MultipartFile;

import com.fruitude.product.model.ProductImage;

final class ImageUploadSupport {
    static final long MAX_SIZE = 5 * 1024 * 1024;
    private ImageUploadSupport() {}

    static void apply(ProductImage image, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("請選擇圖片檔案");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("圖片不可超過 5 MB");
        byte[] data = file.getBytes();
        String type;
        if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') type = "image/png";
        else if (data.length >= 3 && data[0] == (byte) 0xff && data[1] == (byte) 0xd8 && data[2] == (byte) 0xff) type = "image/jpeg";
        else if (data.length >= 6 && new String(data, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a")) type = "image/gif";
        else throw new IllegalArgumentException("只接受 PNG、JPEG 或 GIF 圖片");
        if (ImageIO.read(new ByteArrayInputStream(data)) == null) throw new IllegalArgumentException("圖片內容無法讀取");
        String name = file.getOriginalFilename();
        name = name == null ? "image" : name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "_");
        if (name.length() > 255) throw new IllegalArgumentException("圖片檔名不可超過 255 字");
        image.setImageData(data);
        image.setImageName(name);
        image.setImageType(type);
        image.setFileSize(data.length);
        if (image.getCreatedAt() == null) image.setCreatedAt(LocalDateTime.now());
        image.setUpdatedAt(LocalDateTime.now());
    }
}
