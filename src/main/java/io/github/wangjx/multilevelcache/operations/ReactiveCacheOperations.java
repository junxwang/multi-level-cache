package io.github.wangjx.multilevelcache.operations;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;

/**
 * Reactive Redis 缓存操作实现
 * @author wangjx
 */
public class ReactiveCacheOperations implements CacheOperations {

    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    public ReactiveCacheOperations(ReactiveRedisTemplate<String, Object> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }


    @Override
    public Mono<Object> get(String key) {
        return reactiveRedisTemplate.opsForValue().get(key);
    }

    @Override
    public Mono<Boolean> set(String key, Object value, Duration expire) {
        return reactiveRedisTemplate.opsForValue().set(key, value, expire);
    }

    @Override
    public Mono<Long> delete(String key) {
        return reactiveRedisTemplate.delete(key);
    }

    @Override
    public Mono<Long> delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Mono.just(0L);
        }
        return reactiveRedisTemplate.delete(keys.toArray(new String[0]));
    }

    @Override
    public Mono<Collection<String>> keys(String pattern) {
        return reactiveRedisTemplate.keys(pattern)
                .collectList()
                .cast(Collection.class)
                .map(keys -> (Collection<String>) keys);
    }
}

