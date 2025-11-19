package io.github.wangjx.multilevelcache.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多级缓存配置属性
 * @author wangjx
 */
@ConfigurationProperties(prefix = "cache.multilevel")
public class MultiLevelCacheProperties {
    
    /**
     * 本地缓存最大大小
     */
    private long localCacheMaxSize = 1000;
    
    /**
     * 本地缓存过期时间（秒）
     */
    private long localCacheExpireSeconds = 300;
    
    /**
     * Redis缓存过期时间（秒）
     */
    private long redisCacheExpireSeconds = 3600;
    
    /**
     * Redis操作超时时间（秒）
     */
    private long redisTimeout = 3;
    
    /**
     * 缓存失效广播频道名称
     */
    private String invalidationChannel = "cache:invalidation";

    public long getLocalCacheMaxSize() {
        return localCacheMaxSize;
    }

    public void setLocalCacheMaxSize(long localCacheMaxSize) {
        this.localCacheMaxSize = localCacheMaxSize;
    }

    public long getLocalCacheExpireSeconds() {
        return localCacheExpireSeconds;
    }

    public void setLocalCacheExpireSeconds(long localCacheExpireSeconds) {
        this.localCacheExpireSeconds = localCacheExpireSeconds;
    }

    public long getRedisCacheExpireSeconds() {
        return redisCacheExpireSeconds;
    }

    public void setRedisCacheExpireSeconds(long redisCacheExpireSeconds) {
        this.redisCacheExpireSeconds = redisCacheExpireSeconds;
    }

    public long getRedisTimeout() {
        return redisTimeout;
    }

    public void setRedisTimeout(long redisTimeout) {
        this.redisTimeout = redisTimeout;
    }

    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    public void setInvalidationChannel(String invalidationChannel) {
        this.invalidationChannel = invalidationChannel;
    }
}

