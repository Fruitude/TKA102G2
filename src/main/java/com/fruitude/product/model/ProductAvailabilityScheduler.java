package com.fruitude.product.model;
import java.time.*;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@EnableScheduling
@ConditionalOnProperty(name="product.availability-scheduler.enabled",havingValue="true",matchIfMissing=true)
public class ProductAvailabilityScheduler {
    private final ProductSkuRepository skus; private final ProductAvailabilityService availability;
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());
    public ProductAvailabilityScheduler(ProductSkuRepository skus,ProductAvailabilityService availability){this.skus=skus;this.availability=availability;}
    @Scheduled(fixedDelayString="${product.availability-scheduler.delay-ms:30000}",initialDelayString="${product.availability-scheduler.delay-ms:30000}")
    public void refresh() {
        var now=LocalDateTime.now(ZoneId.of("Asia/Taipei"));var ids=new TreeSet<>(skus.findAvailabilityProductIds());ids.addAll(skus.findDueProductIds(now));
        for(var id:ids)try{availability.refresh(id,now);}catch(RuntimeException error){log.warn("商品 {} 狀態同步失敗，將於下一次重試",id,error);}
    }
}
