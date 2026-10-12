package com.fruitude.product.model;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import org.springframework.web.multipart.MultipartFile;
import com.fruitude.product.model.ProductImage;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

@Service
public class ProductImageService {

    public com.fruitude.product.model.ProductImageRepository.ProductImageSource getImageSource(Integer imageId) { return repository.findImageSource(imageId).orElse(null); }
    public com.fruitude.product.model.ProductImageRepository.ProductThumbnailSource getThumbnailSource(Integer imageId) {
        return repository.findThumbnailSource(imageId).orElse(null);
    }

    @org.springframework.transaction.annotation.Transactional
    public void replaceImage(Integer id, Integer sortOrder, ProductImage replacement) {
        ProductImage original = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("圖片不存在"));
        original.setSortOrder(sortOrder);
        if (replacement.getImageData() != null) {
            original.setImageData(replacement.getImageData());
            original.setImageName(replacement.getImageName());
            original.setImageType(replacement.getImageType());
            original.setFileSize(replacement.getFileSize());
        }
        original.setUpdatedAt(java.time.LocalDateTime.now());
        repository.saveAndFlush(original);
    }

    @Autowired
    ProductImageRepository repository;

    public void addProductImage(ProductImage productImage) {
        repository.save(productImage);
    }

    public void updateProductImage(ProductImage productImage) {
        repository.save(productImage);
    }

    public void deleteProductImage(Integer imageId) {
        if (repository.existsById(imageId)) {
            repository.deleteById(imageId);
        }
    }

    public ProductImage getOneProductImage(Integer imageId) {
        Optional<ProductImage> optional =
                repository.findById(imageId);

        return optional.orElse(null);
    }

    public List<ProductImage> getAll() {
        return repository.findAll(org.springframework.data.domain.Sort.by("productSku.skuId", "sortOrder", "imageId"));
    }

    public static final class ImageUploadSupport {

    public static final long MAX_SIZE = 5 * 1024 * 1024;


    public static void apply(ProductImage image, MultipartFile file) throws IOException {
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

    public static final class ProductThumbnailSupport {


    public static byte[] resize(byte[] original) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
        if (source == null) return null;
        double scale = Math.min(1, 100.0 / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int)Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int)Math.round(source.getHeight() * scale));
        BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = thumbnail.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        var output = new ByteArrayOutputStream();
        ImageIO.write(thumbnail, "jpg", output);
        return output.toByteArray();
    }

    }

}
