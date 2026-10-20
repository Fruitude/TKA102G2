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
    private static final org.slf4j.Logger CACHE_LOG = org.slf4j.LoggerFactory.getLogger(ProductImageService.class);
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.directory:${java.io.tmpdir}/fruitude/product-cache}")
    private String cacheDirectory;
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.ttl-seconds:86400}")
    private long cacheTtlSeconds = 86400;
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.max-bytes:268435456}")
    private long cacheMaxBytes = 268435456;
    // Restart isolation is server-side only. Browser URLs must not change on deployment.
    private final String cacheNamespace = java.util.UUID.randomUUID().toString();
    private final java.util.concurrent.ConcurrentHashMap<Integer,String> imageVersions = new java.util.concurrent.ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.max-concurrent-loads:4}")
    private int maxConcurrentLoads = 4;
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.wait-ms:1500}")
    private long loadWaitMillis = 1500;
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.warmup.enabled:true}")
    private boolean warmupEnabled = true;
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.warmup.batch-size:20}")
    private int warmupBatchSize = 20;
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.warmup.max-products:2000}")
    private int warmupMaxProducts = 2000;
    @org.springframework.beans.factory.annotation.Value("${product.image-cache.warmup.pause-ms:100}")
    private long warmupPauseMillis = 100;
    private volatile java.util.concurrent.Semaphore imageLoads = new java.util.concurrent.Semaphore(4,true);
    private final java.util.concurrent.atomic.AtomicInteger activeRequests = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean warmupStarted = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.ExecutorService warmupWorker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r,"product-image-warmup");thread.setDaemon(true);return thread;
    });
    private final java.util.concurrent.locks.ReentrantLock[] imageLocks = java.util.stream.IntStream.range(0,128)
        .mapToObj(i->new java.util.concurrent.locks.ReentrantLock()).toArray(java.util.concurrent.locks.ReentrantLock[]::new);
    private ImageDiskCache diskCache;

    @jakarta.annotation.PostConstruct
    public void initializeImageCache() {
        if (cacheTtlSeconds <= 0 || cacheMaxBytes <= 0) throw new IllegalArgumentException("圖片快取期限與容量必須大於 0");
        if(maxConcurrentLoads<1 || maxConcurrentLoads>32 || loadWaitMillis<0 || warmupBatchSize<1
                || warmupBatchSize>100 || warmupMaxProducts<0 || warmupPauseMillis<0)
            throw new IllegalArgumentException("圖片限流與預熱設定不正確");
        imageLoads = new java.util.concurrent.Semaphore(maxConcurrentLoads,true);
        diskCache = new ImageDiskCache(java.nio.file.Path.of(cacheDirectory),cacheNamespace,cacheTtlSeconds,cacheMaxBytes);
    }

    // Compute each content version once, then keep it until a committed image change.
    // The same bytes yield the same URL after restart, even with second-precision timestamps.
    public String imageVersion(Integer id) {
        if(id==null || id<=0)return "unversioned";
        String known=imageVersions.get(id);
        if(known!=null)return known;
        if(repository==null)return "unversioned";
        return imageVersions.computeIfAbsent(id,key->repository.findImageFingerprint(key).orElse("missing"));
    }
    // Resolve versions only for IDs actually rendered; repeated pages reuse the version map.
    public java.util.Map<Integer,String> browserVersions() {
        return new java.util.AbstractMap<>() {
            @Override public String get(Object key) { return key instanceof Integer id ? imageVersion(id) : "unversioned"; }
            @Override public java.util.Set<Entry<Integer,String>> entrySet() { return java.util.Set.of(); }
        };
    }

    public record CachedImage(byte[] data,String type,String etag) {}
    public CachedImage getCachedImage(Integer id,boolean thumbnail) throws IOException {
        activeRequests.incrementAndGet();
        try { return readImage(id,thumbnail); }
        finally { activeRequests.decrementAndGet(); }
    }
    private CachedImage readImage(Integer id,boolean thumbnail) throws IOException {
        if(id == null || id <= 0) return null;
        // A disk-cache hit never takes a database/resize permit or queries MySQL.
        CachedImage hit=readCache(id,thumbnail);
        if(hit!=null)return hit;
        boolean acquired=false,locked=false;
        var lock=imageLocks[id % imageLocks.length];
        try {
            // Duplicate requests wait on their image before consuming a global database slot.
            locked=lock.tryLock(loadWaitMillis,java.util.concurrent.TimeUnit.MILLISECONDS);
            if(!locked)throw new ImageBusyException();
            hit=readCache(id,thumbnail);
            if(hit!=null)return hit;
            acquired=imageLoads.tryAcquire(loadWaitMillis,java.util.concurrent.TimeUnit.MILLISECONDS);
            if(!acquired)throw new ImageBusyException();
            CachedImage original = thumbnail ? readCache(id,false) : null;
            if(original == null) {
                if(thumbnail) {
                    var source=getThumbnailSource(id);
                    if(source == null || source.getImageData() == null || source.getImageData().length == 0) return null;
                    original=packed(source.getImageData(),detectImageType(source.getImageData()),false);
                } else {
                    var source=getImageSource(id);
                    if(source == null || source.getImageData() == null || source.getImageData().length == 0) return null;
                    original=packed(source.getImageData(),source.getImageType(),false);
                }
                writeCache(id,false,original);
            }
            imageVersions.put(id,original.etag().substring(1,original.etag().length()-1));
            if(!thumbnail) return original;
            byte[] resized=ProductThumbnailSupport.resize(original.data());
            if(resized == null) return null;
            CachedImage result=packed(resized,"image/jpeg",true);
            writeCache(id,true,result);
            return result;
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();throw new ImageBusyException();
        } finally {
            if(acquired)imageLoads.release();
            if(locked)lock.unlock();
        }
    }
    public static final class ImageBusyException extends IOException {
        public ImageBusyException(){super("圖片載入忙碌中，請稍後重試");}
    }
    private static CachedImage packed(byte[] data,String type,boolean thumbnail) {
        if(!java.util.Set.of("image/png","image/jpeg","image/gif").contains(type == null ? "" : type)) type="application/octet-stream";
        return new CachedImage(data,type,"\""+fingerprint(data)+(thumbnail?"-thumb100":"")+"\"");
    }
    private static String fingerprint(byte[] data) {
        try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmupImages() {
        if(!warmupEnabled || repository==null || stopped.get() || !warmupStarted.compareAndSet(false,true))return;
        warmupWorker.submit(this::warmup);
    }
    private void warmup() {
        int prepared=0;
        try {
            for(int offset=0;offset<warmupMaxProducts && !stopped.get();offset+=warmupBatchSize) {
                int size=Math.min(warmupBatchSize,warmupMaxProducts-offset);
                var ids=repository.findWarmupImageIds(offset,size);
                if(ids.isEmpty())break;
                for(var id:ids) {
                    if(stopped.get())return;
                    if(id==null)continue;
                    while(activeRequests.get()>0 && !stopped.get())Thread.sleep(Math.max(10,warmupPauseMillis));
                    if(stopped.get())return;
                    try {if(readImage(id,true)!=null)prepared++;}
                    catch(Exception failure){CACHE_LOG.warn("商品圖片 {} 預熱失敗，訪客仍可按需載入：{}",id,failure.getMessage());}
                    Thread.sleep(warmupPauseMillis);
                }
                if(ids.size()<size)break;
            }
            CACHE_LOG.info("商品圖片預熱完成，共 {} 張代表圖片",prepared);
        } catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(Exception failure){CACHE_LOG.warn("商品圖片預熱中止，維持按需載入",failure);}
    }
    @jakarta.annotation.PreDestroy
    public void stopImageWarmup(){stopped.set(true);warmupWorker.shutdownNow();}
    private static String detectImageType(byte[] data) {
        if(data.length>=8 && data[0]==(byte)0x89 && data[1]=='P' && data[2]=='N' && data[3]=='G') return "image/png";
        if(data.length>=3 && data[0]==(byte)0xff && data[1]==(byte)0xd8 && data[2]==(byte)0xff) return "image/jpeg";
        if(data.length>=6 && new String(data,0,6,java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a")) return "image/gif";
        return "application/octet-stream";
    }
    private CachedImage readCache(Integer id,boolean thumbnail) {
        if(diskCache==null) return null;
        try { return diskCache.read(id,thumbnail); }
        catch(IOException failure) { CACHE_LOG.warn("商品圖片 {} 快取讀取失敗，改讀資料庫：{}",id,failure.getMessage()); return null; }
    }
    private void writeCache(Integer id,boolean thumbnail,CachedImage image) {
        if(diskCache==null) return;
        try { diskCache.write(id,thumbnail,image); }
        catch(IOException failure) { CACHE_LOG.warn("商品圖片 {} 快取寫入失敗，仍回傳圖片：{}",id,failure.getMessage()); }
    }
    public void invalidateImageAfterCommit(Integer id) {
        if(id==null) return;
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { invalidateImage(id); }
            });
        } else invalidateImage(id);
    }
    private void invalidateImage(Integer id) {
        var lock=imageLocks[Math.floorMod(id,imageLocks.length)];lock.lock();
        try {
            if(diskCache!=null) try { diskCache.remove(id); }
            catch(IOException failure) { CACHE_LOG.warn("商品圖片 {} 舊快取移除失敗：{}",id,failure.getMessage()); }
            // Make old bytes unreadable before a new version can be published.
            imageVersions.remove(id);
        } finally {lock.unlock();}
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${product.image-cache.cleanup-ms:3600000}",initialDelayString="${product.image-cache.cleanup-ms:3600000}")
    public void cleanupImageCache() {
        if(diskCache!=null) try { diskCache.cleanup(); }
        catch(IOException failure) { CACHE_LOG.warn("商品圖片快取清理失敗：{}",failure.getMessage()); }
    }

    // Covers direct Repository saves and images changed/deleted through product/SKU cascades.
    @org.springframework.stereotype.Component
    public static class ImageCacheListener {
        @org.springframework.beans.factory.annotation.Autowired
        private org.springframework.beans.factory.ObjectProvider<ProductImageService> service;
        @jakarta.persistence.PostPersist @jakarta.persistence.PostUpdate @jakarta.persistence.PostRemove
        public void changed(ProductImage image) { service.getObject().invalidateImageAfterCommit(image.getImageId()); }
    }

    static final class ImageDiskCache {
        private final java.nio.file.Path root;
        private final java.nio.file.Path directory;
        private final long ttlMillis,maxBytes;
        private long accountedBytes=-1;
        private final java.util.Set<Integer> invalidated=java.util.concurrent.ConcurrentHashMap.newKeySet();
        ImageDiskCache(java.nio.file.Path base,String namespace,long ttlSeconds,long maxBytes) {
            root=base.toAbsolutePath().normalize().resolve("product-images-cache-v1");
            directory=root.resolve(namespace);ttlMillis=Math.multiplyExact(ttlSeconds,1000L);this.maxBytes=maxBytes;
        }
        private java.nio.file.Path file(int id,boolean thumbnail) { return directory.resolve(id+(thumbnail?"-thumb100":"-original")+".pic"); }
        CachedImage read(int id,boolean thumbnail) throws IOException {
            if(invalidated.contains(id)) return null;
            var file=file(id,thumbnail);
            if(!java.nio.file.Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)) return null;
            try(var input=new java.io.DataInputStream(java.nio.file.Files.newInputStream(file))) {
                if(input.readInt()!=0x46525549 || input.readLong()<System.currentTimeMillis()) { java.nio.file.Files.deleteIfExists(file);return null; }
                String type=input.readUTF(),etag=input.readUTF();int length=input.readInt();
                if(length<=0 || length>16*1024*1024 || java.nio.file.Files.size(file)>16*1024*1024+1024) throw new IOException("快取格式異常");
                byte[] data=input.readNBytes(length);if(data.length!=length || input.read()!=-1) throw new IOException("快取不完整");
                if(!packed(data,type,thumbnail).etag().equals(etag)) throw new IOException("快取內容損毀");
                return new CachedImage(data,type,etag);
            } catch(IOException failure) { java.nio.file.Files.deleteIfExists(file);throw failure; }
        }
        synchronized void write(int id,boolean thumbnail,CachedImage image) throws IOException {
            if(image.data().length>16*1024*1024 || image.data().length+1024>maxBytes) return;
            if(invalidated.contains(id)) remove(id);
            if(accountedBytes<0)cleanup();
            java.nio.file.Files.createDirectories(directory);
            var target=file(id,thumbnail);var temp=java.nio.file.Files.createTempFile(directory,"writing-",".tmp");
            long replaced=java.nio.file.Files.isRegularFile(target)?java.nio.file.Files.size(target):0;
            try {
                try(var output=new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(temp))) {
                    output.writeInt(0x46525549);output.writeLong(System.currentTimeMillis()+ttlMillis);
                    output.writeUTF(image.type());output.writeUTF(image.etag());output.writeInt(image.data().length);output.write(image.data());
                }
                try { java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch(java.nio.file.AtomicMoveNotSupportedException ignored) { java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                invalidated.remove(id);
                accountedBytes+=java.nio.file.Files.size(target)-replaced;
                // Warmup should not scan the whole directory for every image written.
                if(accountedBytes>maxBytes)cleanup();
            } finally { java.nio.file.Files.deleteIfExists(temp); }
        }
        void remove(int id) throws IOException {
            // Prevent a failed deletion from serving stale bytes again in this process.
            invalidated.add(id);
            java.nio.file.Files.deleteIfExists(file(id,false));java.nio.file.Files.deleteIfExists(file(id,true));
        }
        synchronized void cleanup() throws IOException {
            if(!java.nio.file.Files.isDirectory(root)) {accountedBytes=0;return;}
            java.util.List<java.nio.file.Path> files;
            try(var paths=java.nio.file.Files.walk(root,2)) {
                files=paths.filter(p->java.nio.file.Files.isRegularFile(p,java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(p->p.getFileName().toString().matches("[0-9]+-(original|thumb100)\\.pic")).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            }
            long now=System.currentTimeMillis(),total=0;
            var times=new java.util.HashMap<java.nio.file.Path,Long>();
            var retained=new java.util.ArrayList<java.nio.file.Path>();
            for(var p:files) {
                try {long time=java.nio.file.Files.getLastModifiedTime(p).toMillis();
                    if(now-time>=ttlMillis) java.nio.file.Files.deleteIfExists(p);
                    else {total+=java.nio.file.Files.size(p);times.put(p,time);retained.add(p);}
                }catch(java.nio.file.NoSuchFileException ignored){}
            }
            retained.sort(java.util.Comparator.comparingLong(times::get));
            for(var p:retained) { if(total<=maxBytes) break;try {long size=java.nio.file.Files.size(p);java.nio.file.Files.deleteIfExists(p);total-=size;}catch(java.nio.file.NoSuchFileException ignored){} }
            accountedBytes=total;
        }
    }



    public Integer findProductImageId(Integer productId) {
        if (productId == null || productId <= 0) return null;
        return repository.findFirstImageIdForProduct(productId).orElse(null);
    }

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
