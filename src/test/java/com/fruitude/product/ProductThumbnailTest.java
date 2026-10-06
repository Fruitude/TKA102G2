package com.fruitude.product;

import com.fruitude.product.controller.ProductImageController;
import com.fruitude.product.controller.ProductThumbnailSupport;
import com.fruitude.product.model.ProductImageService;
import com.fruitude.product.model.ProductThumbnailSource;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ProductThumbnailTest {
    @Test public void originalImageCanBeCachedForTenMinutesWithoutLoadingEntityRelations() throws Exception {
        var controller = new ProductImageController();
        ReflectionTestUtils.setField(controller, "productImageService", new ProductImageService() {
            @Override public com.fruitude.product.model.ProductImageSource getImageSource(Integer id) {
                return new com.fruitude.product.model.ProductImageSource() {
                    public byte[] getImageData() { return new byte[]{1,2,3}; }
                    public String getImageType() { return "image/png"; }
                };
            }
            @Override public com.fruitude.product.model.ProductImage getOneProductImage(Integer id) { throw new AssertionError("entity should not be loaded"); }
        });
        var response = MockMvcBuilders.standaloneSetup(controller).build().perform(get("/product/image/7")).andReturn().getResponse();
        assertEquals(200, response.getStatus()); assertEquals("image/png", response.getContentType());
        assertTrue(response.getHeader("Cache-Control").contains("max-age=600")); assertTrue(response.getHeader("Cache-Control").contains("private"));
    }
    private byte[] image(int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    @Test public void thumbnailBoundsDimensionsAndPreservesAspectRatioWithoutUpscaling() throws Exception {
        var wide = ImageIO.read(new ByteArrayInputStream(ProductThumbnailSupport.resize(image(800, 400))));
        assertEquals(100, wide.getWidth()); assertEquals(50, wide.getHeight());
        var tall = ImageIO.read(new ByteArrayInputStream(ProductThumbnailSupport.resize(image(200, 800))));
        assertEquals(25, tall.getWidth()); assertEquals(100, tall.getHeight());
        var small = ImageIO.read(new ByteArrayInputStream(ProductThumbnailSupport.resize(image(20, 30))));
        assertEquals(20, small.getWidth()); assertEquals(30, small.getHeight());
    }

    @Test public void thumbnailSupportsRevalidationAndChangedImagesInvalidateEtag() throws Exception {
        byte[][] current = { image(800, 400) };
        var controller = new ProductImageController();
        ReflectionTestUtils.setField(controller, "productImageService", new ProductImageService() {
            @Override public ProductThumbnailSource getThumbnailSource(Integer id) { return () -> current[0]; }
        });
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var first = mvc.perform(get("/product/image/7/thumbnail")).andReturn().getResponse();
        assertEquals(200, first.getStatus()); assertEquals("image/jpeg", first.getContentType());
        assertTrue(first.getHeader("Cache-Control").contains("no-cache"));
        String etag = first.getHeader("ETag"); assertNotNull(etag);
        var unchanged = mvc.perform(get("/product/image/7/thumbnail").header("If-None-Match", etag)).andReturn().getResponse();
        assertEquals(304, unchanged.getStatus()); assertEquals(0, unchanged.getContentAsByteArray().length);
        current[0] = image(400, 800);
        var changed = mvc.perform(get("/product/image/7/thumbnail").header("If-None-Match", etag)).andReturn().getResponse();
        assertEquals(200, changed.getStatus()); assertNotEquals(etag, changed.getHeader("ETag"));
    }

    @Test public void missingOrInvalidImageReturnsNotFound() throws Exception {
        var controller = new ProductImageController();
        ReflectionTestUtils.setField(controller, "productImageService", new ProductImageService() {
            @Override public ProductThumbnailSource getThumbnailSource(Integer id) { return id==1 ? null : () -> new byte[]{1, 2, 3}; }
        });
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(404, mvc.perform(get("/product/image/1/thumbnail")).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/product/image/2/thumbnail")).andReturn().getResponse().getStatus());
    }
}
