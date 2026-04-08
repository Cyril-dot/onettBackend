package com.marketPlace.MarketPlace.Config.Security.RateLimitingConfigs;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class RateLimitingService {

    private final Cache<String,Bucket> cache;
    private final long capacity;
    private final long refillSeconds;

    public RateLimitingService(RateLimitingProperties properties){
        this.capacity = properties.getCapacity();
        this.refillSeconds = properties.getRefillSeconds();

        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getMaxCacheSize())
                .expireAfterAccess(Duration.ofSeconds(refillSeconds))
                .recordStats()
                .build();

        log.info("Rate limiting service initialized - Capacity: {} request per {} minutes",capacity, refillSeconds);
    }

    public Bucket resolveBucket(String key) {
        return cache.get(key, k -> createNewBucket());
    }


    // to create new bucket
    private Bucket createNewBucket(){
        Refill refill = Refill.of(capacity, Duration.ofSeconds(refillSeconds));
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    // to consume the bucket
    public boolean tryConsume(String key) {
        Bucket bucket = resolveBucket(key);
        return bucket.tryConsume(1);
    }

    // to get avaliable tokens for the key
    public long getAvaliableTokens(String key){
        Bucket bucket = resolveBucket(key);
        return bucket.getAvailableTokens();
    }

    // get time until refil in seconds
    public long getSecondsUntilRefil(String key){
        Bucket bucket = resolveBucket(key);
        return bucket.getAvailableTokens() == 0 ?
                (refillSeconds) - (System.currentTimeMillis() / 1000 % refillSeconds) : 0;
    }


    // to get cache statistics
    public Map<String , Object> getCacheStats() {
        var stats = cache.stats();
        return Map.of(
                "hitRate", stats.hitRate(),
                "missRate", stats.missRate(),
                "evictionCount", stats.evictionCount(),
                "size", cache.estimatedSize()
        );
    }

    // to clear cache
    public void clearCache(){
        cache.invalidateAll();
        log.info("Cache cleared");
    }


    // to remove specifc key from cache
    public void removeKey(String key){
        cache.invalidate(key);
        log.info("Key {} removed from cache", key);
    }




    // hashmap , key = letters, value = token of tokens
    // one time search



}
