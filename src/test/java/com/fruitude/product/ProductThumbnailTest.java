package com.fruitude.product;

import com.fruitude.product.controller.ProductImageController;
import com.fruitude.product.model.ProductImageService.ProductThumbnailSupport;
import com.fruitude.product.model.ProductImageService;
import com.fruitude.product.model.ProductImageRepository.ProductThumbnailSource;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ProductThumbnailTest {
    @Test public void versionedOriginalCanBeCachedForThirtyMinutesWithoutLoadingEntityRelations() throws Exception {
        var controller = new ProductImageController();
        var service = new ProductImageService() {
            @Override public com.fruitude.product.model.ProductImageRepository.ProductImageSource getImageSource(Integer id) {
                return new com.fruitude.product.model.ProductImageRepository.ProductImageSource() {
                    public byte[] getImageData() { return new byte[]{1,2,3}; }
                    public String getImageType() { return "image/png"; }
                };
            }
            @Override public com.fruitude.product.model.ProductImage getOneProductImage(Integer id) { throw new AssertionError("entity should not be loaded"); }
        };
        ReflectionTestUtils.setField(controller,"productImageService",service);
        var response = MockMvcBuilders.standaloneSetup(controller).build().perform(get("/product/image/7").param("v",service.imageVersion(7))).andReturn().getResponse();
        assertEquals(200, response.getStatus()); assertEquals("image/png", response.getContentType());
        assertTrue(response.getHeader("Cache-Control").contains("max-age=1800")); assertTrue(response.getHeader("Cache-Control").contains("private"));
    }
    private byte[] image(int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    @Test public void thumbnailBoundsDimensionsAndPreservesAspectRatioWithoutUpscaling() throws Exception {
        var wide = ImageIO.read(new ByteArrayInputStream(com.fruitude.product.model.ProductImageService.ProductThumbnailSupport.resize(image(800, 400))));
        assertEquals(100, wide.getWidth()); assertEquals(50, wide.getHeight());
        var tall = ImageIO.read(new ByteArrayInputStream(com.fruitude.product.model.ProductImageService.ProductThumbnailSupport.resize(image(200, 800))));
        assertEquals(25, tall.getWidth()); assertEquals(100, tall.getHeight());
        var small = ImageIO.read(new ByteArrayInputStream(com.fruitude.product.model.ProductImageService.ProductThumbnailSupport.resize(image(20, 30))));
        assertEquals(20, small.getWidth()); assertEquals(30, small.getHeight());
    }

    @Test public void thumbnailSupportsRevalidationAndChangedImagesInvalidateEtag() throws Exception {
        byte[][] current = { image(800, 400) };
        var controller = new ProductImageController();
        ReflectionTestUtils.setField(controller, "productImageService", new ProductImageService() {
            @Override public com.fruitude.product.model.ProductImageRepository.ProductThumbnailSource getThumbnailSource(Integer id) { return () -> current[0]; }
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
            @Override public com.fruitude.product.model.ProductImageRepository.ProductThumbnailSource getThumbnailSource(Integer id) { return id==1 ? null : () -> new byte[]{1, 2, 3}; }
        });
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(404, mvc.perform(get("/product/image/1/thumbnail")).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/product/image/2/thumbnail")).andReturn().getResponse().getStatus());
    }
    private ProductImageService cachedService(java.nio.file.Path directory,byte[][] bytes,
            java.util.concurrent.atomic.AtomicInteger queries) {
        var service=new ProductImageService() {
            @Override public com.fruitude.product.model.ProductImageRepository.ProductImageSource getImageSource(Integer id) {
                queries.incrementAndGet();if(bytes[0]==null)return null;
                byte[] current=bytes[0].clone();
                return new com.fruitude.product.model.ProductImageRepository.ProductImageSource() {
                    public byte[] getImageData(){return current;}public String getImageType(){return "image/png";}
                };
            }
            @Override public ProductThumbnailSource getThumbnailSource(Integer id) {
                queries.incrementAndGet();return bytes[0]==null?null:()->bytes[0];
            }
        };
        ReflectionTestUtils.setField(service,"cacheDirectory",directory.toString());service.initializeImageCache();return service;
    }
    private final java.util.List<java.nio.file.Path> temporary=new java.util.ArrayList<>();
    private java.nio.file.Path cacheDirectory() throws Exception {
        var dir=java.nio.file.Files.createTempDirectory("fruitude-image-cache-test-");temporary.add(dir);return dir;
    }
    @org.junit.After public void removeTemporaryCaches() throws Exception {
        for(var dir:temporary)try(var files=java.nio.file.Files.walk(dir)){for(var p:files.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(p);}
    }
    @Test public void originalAndThumbnailReuseDiskCacheWithoutAnotherBlobQuery() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();byte[][] data={image(800,400)};
        var service=cachedService(cacheDirectory(),data,count);
        assertArrayEquals(data[0],service.getCachedImage(7,false).data());
        var thumbnail=service.getCachedImage(7,true);assertEquals("image/jpeg",thumbnail.type());
        assertArrayEquals(thumbnail.data(),service.getCachedImage(7,true).data());
        assertArrayEquals(data[0],service.getCachedImage(7,false).data());assertEquals(1,count.get());
    }
    @Test public void concurrentColdReadsFetchTheBlobOnce() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();byte[][] data={image(800,400)};
        var service=cachedService(cacheDirectory(),data,count);var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try {var futures=new java.util.ArrayList<java.util.concurrent.Future<byte[]>>();
            for(int i=0;i<16;i++)futures.add(pool.submit(()->service.getCachedImage(7,true).data()));
            byte[] first=futures.get(0).get();for(var future:futures)assertArrayEquals(first,future.get());assertEquals(1,count.get());
        }finally{pool.shutdownNow();}
    }
    @Test public void imageListenerInvalidatesBothSizesOnlyAfterCommitAndRollbackKeepsThem() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();byte[][] data={image(800,400)};
        var service=cachedService(cacheDirectory(),data,count);
        var beans=new org.springframework.beans.factory.support.StaticListableBeanFactory();beans.addBean("images",service);
        var listener=new ProductImageService.ImageCacheListener();ReflectionTestUtils.setField(listener,"service",beans.getBeanProvider(ProductImageService.class));
        var entity=new com.fruitude.product.model.ProductImage();entity.setImageId(7);
        String old=service.imageVersion(7);var original=service.getCachedImage(7,false);var thumbnail=service.getCachedImage(7,true);
        var tx=org.springframework.transaction.support.TransactionSynchronizationManager.class;
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {listener.changed(entity);assertEquals(old,service.imageVersion(7));}
        finally {org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
        assertEquals(old,service.imageVersion(7));assertEquals(1,count.get());assertArrayEquals(original.data(),service.getCachedImage(7,false).data());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {listener.changed(entity);data[0]=image(400,800);for(var callback:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())callback.afterCommit();}
        finally {org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
        assertNotEquals(old,service.imageVersion(7));assertNotEquals(original.etag(),service.getCachedImage(7,false).etag());
        assertNotEquals(thumbnail.etag(),service.getCachedImage(7,true).etag());assertEquals(2,count.get());
        data[0]=null;listener.changed(entity);assertNull(service.getCachedImage(7,false));assertNull(service.getCachedImage(7,true));
    }
    @Test public void corruptedAndExpiredFilesAreRebuiltAndCacheCapacityIsBounded() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();byte[][] data={image(800,400)};var dir=cacheDirectory();
        var service=cachedService(dir,data,count);service.getCachedImage(7,false);
        java.nio.file.Path file;try(var paths=java.nio.file.Files.walk(dir)){file=paths.filter(p->p.toString().endsWith(".pic")).findFirst().orElseThrow();}
        java.nio.file.Files.write(file,new byte[]{0});assertArrayEquals(data[0],service.getCachedImage(7,false).data());assertEquals(2,count.get());
        try(var writer=new java.io.RandomAccessFile(file.toFile(),"rw")){writer.seek(4);writer.writeLong(0);}
        assertNotNull(service.getCachedImage(7,false));assertEquals(3,count.get());
        ReflectionTestUtils.setField(service,"cacheMaxBytes",(long)data[0].length+1100);service.initializeImageCache();
        service.getCachedImage(8,false);service.getCachedImage(9,false);
        try(var paths=java.nio.file.Files.walk(dir)){long total=paths.filter(p->p.toString().endsWith(".pic")).mapToLong(p->{try{return java.nio.file.Files.size(p);}catch(Exception e){throw new RuntimeException(e);}}).sum();assertTrue(total<=data[0].length+1100);}
    }
    @Test public void unwritableCacheStillServesImagesAndVersionedBrowserCacheRevalidates() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();byte[][] data={image(800,400)};var dir=cacheDirectory();
        var blocking=dir.resolve("not-a-directory");java.nio.file.Files.writeString(blocking,"blocking");
        var fallback=cachedService(blocking,data,count);assertArrayEquals(data[0],fallback.getCachedImage(7,false).data());
        var service=cachedService(dir.resolve("good"),data,new java.util.concurrent.atomic.AtomicInteger());
        var controller=new ProductImageController();ReflectionTestUtils.setField(controller,"productImageService",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();String version=service.imageVersion(7);
        var first=mvc.perform(get("/product/image/7").param("v",version)).andReturn().getResponse();
        assertTrue(first.getHeader("Cache-Control").contains("max-age=1800"));
        assertEquals(304,mvc.perform(get("/product/image/7").param("v",version).header("If-None-Match",first.getHeader("ETag"))).andReturn().getResponse().getStatus());
        data[0]=image(400,800);service.invalidateImageAfterCommit(7);
        var stale=mvc.perform(get("/product/image/7").param("v",version).header("If-None-Match",first.getHeader("ETag"))).andReturn().getResponse();
        assertEquals(200,stale.getStatus());assertTrue(stale.getHeader("Cache-Control").contains("no-cache"));
    }
    @Test public void favoriteImageResolvesCurrentIdEveryTimeAndKeepsContextPath() throws Exception {
        var current=new java.util.concurrent.atomic.AtomicInteger(180);
        var service=new ProductImageService(){
            @Override public Integer findProductImageId(Integer productId){assertEquals(Integer.valueOf(70),productId);return current.get();}
        };
        var controller=new com.fruitude.product.controller.FrontProductController(null,null,null);
        ReflectionTestUtils.setField(controller,"imageCache",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        var first=mvc.perform(get("/shop/front/api/product-image/70").contextPath("/shop")).andReturn().getResponse();
        assertEquals(302,first.getStatus());assertEquals("no-store",first.getHeader("Cache-Control"));
        assertEquals("/shop/product/image/180?v="+service.imageVersion(180),first.getHeader("Location"));
        current.set(991);
        var next=mvc.perform(get("/front/api/product-image/70")).andReturn().getResponse();
        assertEquals("/product/image/991?v="+service.imageVersion(991),next.getHeader("Location"));
    }
    @Test public void favoriteImageHandlesInvalidAndMissingProducts() throws Exception {
        var service=new ProductImageService(){@Override public Integer findProductImageId(Integer id){assertTrue(id>0);return null;}};
        var controller=new com.fruitude.product.controller.FrontProductController(null,null,null);ReflectionTestUtils.setField(controller,"imageCache",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(400,mvc.perform(get("/front/api/product-image/0")).andReturn().getResponse().getStatus());
        assertEquals(404,mvc.perform(get("/front/api/product-image/999999")).andReturn().getResponse().getStatus());
    }
    @Test public void favoriteImageLookupReadsOnlyTheProductBoundImageId() {
        var repository=(com.fruitude.product.model.ProductImageRepository)java.lang.reflect.Proxy.newProxyInstance(
            com.fruitude.product.model.ProductImageRepository.class.getClassLoader(),new Class[]{com.fruitude.product.model.ProductImageRepository.class},(o,m,a)->{
                assertEquals("findFirstImageIdForProduct",m.getName());assertEquals(Integer.valueOf(70),a[0]);return java.util.Optional.of(180);
            });
        var service=new ProductImageService();ReflectionTestUtils.setField(service,"repository",repository);
        assertNull(service.findProductImageId(null));assertNull(service.findProductImageId(-1));assertEquals(Integer.valueOf(180),service.findProductImageId(70));
    }
}
