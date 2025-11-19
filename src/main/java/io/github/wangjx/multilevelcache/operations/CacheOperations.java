package io.github.wangjx.multilevelcache.operations;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;

/**
 * 统一的缓存操作接口，支持 Reactive 和普通 Redis
 * @author wangjx
 */
public interface CacheOperations {
    
    /**
     * 获取缓存值
     * @param key Redis key
     * @return Mono<Object> 缓存值，如果不存在返回 Mono.empty()
     */
    Mono<Object> get(String key);
    
    /**
     * 设置缓存值
     * @param key Redis key
     * @param value 缓存值
     * @param expire 过期时间
     * @return Mono<Boolean> 操作结果
     */
    Mono<Boolean> set(String key, Object value, Duration expire);
    
    /**
     * 删除缓存
     * @param key Redis key
     * @return Mono<Long> 删除的 key 数量
     */
    Mono<Long> delete(String key);
    
    /**
     * 批量删除缓存
     * @param keys Redis keys
     * @return Mono<Long> 删除的 key 数量
     */
    Mono<Long> delete(Collection<String> keys);
    
    /**
     * 查找匹配的 keys
     * @param pattern 匹配模式
     * @return Mono<Collection<String>> 匹配的 keys
     */
    Mono<Collection<String>> keys(String pattern);
}

