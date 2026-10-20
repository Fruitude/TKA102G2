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
    @Test public void currentContentVersionCanBeCachedWithoutLoadingEntityRelations() throws Exception {
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
        var response = MockMvcBuilders.standaloneSetup(controller).build().perform(get("/product/image/7").param("v",sha(new byte[]{1,2,3}))).andReturn().getResponse();
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
        var original=service.getCachedImage(7,false);var thumbnail=service.getCachedImage(7,true);String old=service.imageVersion(7);
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
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();String version=sha(data[0]);
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

    private static String sha(byte[] data) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
    }
    private com.fruitude.product.model.ProductImageRepository fingerprints(byte[][] current,
            java.util.concurrent.atomic.AtomicInteger checks) {
        return (com.fruitude.product.model.ProductImageRepository)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{com.fruitude.product.model.ProductImageRepository.class},(o,m,a)->{
                if(m.getName().equals("findImageFingerprint")) {
                    checks.incrementAndGet();return current[0]==null?java.util.Optional.empty():java.util.Optional.of(sha(current[0]));
                }
                throw new AssertionError(m.getName());
            });
    }
    @Test public void versionsAreMemoizedAndExplicitImportInvalidationRefreshesBothSizes() throws Exception {
        byte[][] data={image(800,400)};var blobs=new java.util.concurrent.atomic.AtomicInteger();var checks=new java.util.concurrent.atomic.AtomicInteger();
        var service=cachedService(cacheDirectory(),data,blobs);ReflectionTestUtils.setField(service,"repository",fingerprints(data,checks));
        var controller=new ProductImageController();ReflectionTestUtils.setField(controller,"productImageService",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        String version=service.imageVersion(7);assertEquals(sha(data[0]),version);
        assertEquals(version,service.browserVersions().get(7));assertEquals(1,checks.get());
        var first=mvc.perform(get("/product/image/7/thumbnail").param("v",version)).andReturn().getResponse();
        assertEquals(200,first.getStatus());String etag=first.getHeader("ETag");
        assertTrue(first.getHeader("Cache-Control").contains("max-age=1800"));
        assertEquals(304,mvc.perform(get("/product/image/7/thumbnail").param("v",version).header("If-None-Match",etag)).andReturn().getResponse().getStatus());
        assertEquals(1,blobs.get());assertEquals(1,checks.get());
        // SQL imports bypass JPA events: maintenance explicitly invalidates (or restarts).
        data[0]=image(400,800);
        service.invalidateImageAfterCommit(7);
        String replacement=service.imageVersion(7);assertNotEquals(version,replacement);assertEquals(2,checks.get());
        var changed=mvc.perform(get("/product/image/7/thumbnail").param("v",replacement).header("If-None-Match",etag)).andReturn().getResponse();
        assertEquals(200,changed.getStatus());assertNotEquals(etag,changed.getHeader("ETag"));assertEquals(2,blobs.get());
        assertEquals(2,checks.get());
        data[0]=null;service.invalidateImageAfterCommit(7);
        var deleted=mvc.perform(get("/product/image/7/thumbnail")).andReturn().getResponse();
        assertEquals(404,deleted.getStatus());assertEquals("no-store",deleted.getHeader("Cache-Control"));
        assertNull(service.getCachedImage(7,false));
    }
    @Test public void restartKeepsBrowserUrlAndEtagButDoesNotTrustOldServerCache() throws Exception {
        byte[][] data={image(100,50)};var dir=cacheDirectory();var count=new java.util.concurrent.atomic.AtomicInteger();
        var first=cachedService(dir,data,count);var saved=first.getCachedImage(7,false);
        var restarted=cachedService(dir,data,count);
        ReflectionTestUtils.setField(restarted,"repository",fingerprints(data,new java.util.concurrent.atomic.AtomicInteger()));
        assertEquals(first.imageVersion(7),restarted.imageVersion(7));assertEquals(saved.etag(),restarted.getCachedImage(7,false).etag());
        assertEquals(2,count.get());
        data[0]=image(50,100);
        var imported=cachedService(dir,data,count);assertNotEquals(saved.etag(),imported.getCachedImage(7,false).etag());
        assertNotEquals(first.imageVersion(7),imported.imageVersion(7));
        assertEquals(3,count.get());
    }
    @Test public void globalLimitBoundsDatabaseReadsAndReturnsRetryable503() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(2);var release=new java.util.concurrent.CountDownLatch(1);
        var running=new java.util.concurrent.atomic.AtomicInteger();var maximum=new java.util.concurrent.atomic.AtomicInteger();
        var service=new ProductImageService(){
            @Override public com.fruitude.product.model.ProductImageRepository.ProductImageSource getImageSource(Integer id){
                if(id==99)return new com.fruitude.product.model.ProductImageRepository.ProductImageSource(){
                    public byte[] getImageData(){return new byte[]{1,2,3};}public String getImageType(){return "image/png";}
                };
                int active=running.incrementAndGet();maximum.accumulateAndGet(active,Math::max);entered.countDown();
                try {if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("timeout");}
                catch(InterruptedException e){throw new AssertionError(e);}finally{running.decrementAndGet();}
                return new com.fruitude.product.model.ProductImageRepository.ProductImageSource(){
                    public byte[] getImageData(){return new byte[]{1,2,3};}public String getImageType(){return "image/png";}
                };
            }
        };
        ReflectionTestUtils.setField(service,"cacheDirectory",cacheDirectory().toString());
        ReflectionTestUtils.setField(service,"maxConcurrentLoads",2);ReflectionTestUtils.setField(service,"loadWaitMillis",30L);service.initializeImageCache();
        var cached=service.getCachedImage(99,false);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->service.getCachedImage(1,false));var two=pool.submit(()->service.getCachedImage(2,false));
            assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
            var controller=new ProductImageController();ReflectionTestUtils.setField(controller,"productImageService",service);
            var mvc=MockMvcBuilders.standaloneSetup(controller).build();
            var hit=mvc.perform(get("/product/image/99").param("v",service.imageVersion(99)).header("If-None-Match",cached.etag())).andReturn().getResponse();
            assertEquals(304,hit.getStatus());assertTrue(hit.getHeader("Cache-Control").contains("max-age=1800"));
            var response=mvc.perform(get("/product/image/3")).andReturn().getResponse();
            assertEquals(503,response.getStatus());assertEquals("1",response.getHeader("Retry-After"));assertEquals("no-store",response.getHeader("Cache-Control"));
            assertEquals(2,maximum.get());release.countDown();assertNotNull(one.get());assertNotNull(two.get());
        } finally {release.countDown();pool.shutdownNow();service.stopImageWarmup();}
    }
    @Test public void backgroundWarmupContinuesAfterBadImageAndReusesPreparedCache() throws Exception {
        byte[][] data={image(800,400)};var reads=new java.util.concurrent.atomic.AtomicInteger();var complete=new java.util.concurrent.CountDownLatch(1);
        var service=new ProductImageService(){
            @Override public ProductThumbnailSource getThumbnailSource(Integer id){
                reads.incrementAndGet();if(id==7)throw new IllegalArgumentException("bad image");return ()->data[0];
            }
        };
        var repository=(com.fruitude.product.model.ProductImageRepository)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{com.fruitude.product.model.ProductImageRepository.class},(o,m,a)->{
                if(m.getName().equals("findWarmupImageIds")){
                    if(((Integer)a[0])==0)return java.util.List.of(7,8);
                    complete.countDown();return java.util.List.of();
                }
                if(m.getName().equals("findImageFingerprint"))return java.util.Optional.of(sha(data[0]));
                throw new AssertionError(m.getName());
            });
        ReflectionTestUtils.setField(service,"repository",repository);ReflectionTestUtils.setField(service,"cacheDirectory",cacheDirectory().toString());
        ReflectionTestUtils.setField(service,"warmupBatchSize",2);ReflectionTestUtils.setField(service,"warmupPauseMillis",0L);service.initializeImageCache();
        try {
            service.warmupImages();service.warmupImages();assertTrue(complete.await(5,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(2,reads.get());assertEquals("image/jpeg",service.getCachedImage(8,true).type());assertEquals(2,reads.get());
        } finally {service.stopImageWarmup();}
    }
}
