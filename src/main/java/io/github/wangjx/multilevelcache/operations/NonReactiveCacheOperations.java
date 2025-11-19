package io.github.wangjx.multilevelcache.operations;

import org.springframework.data.redis.core.RedisTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/**
 * 普通 Redis 缓存操作实现（包装为 Mono）
 * @author wangjx
 */
public class NonReactiveCacheOperations implements CacheOperations {

    private final RedisTemplate<String, Object> redisTemplate;

    public NonReactiveCacheOperations(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Object> get(String key) {
        return Mono.fromCallable(() -> redisTemplate.opsForValue().get(key))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> set(String key, Object value, Duration expire) {
        return Mono.fromCallable(() -> {
                    if (expire != null && !expire.isZero() && !expire.isNegative()) {
                        redisTemplate.opsForValue().set(key, value, expire);
                    } else {
                        redisTemplate.opsForValue().set(key, value);
                    }
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> delete(String key) {
        return Mono.fromCallable(() -> {
                    Boolean result = redisTemplate.delete(key);
                    return Boolean.TRUE.equals(result) ? 1L : 0L;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Mono.just(0L);
        }
        return Mono.fromCallable(() -> {
                    Long count = redisTemplate.delete(keys);
                    return count != null ? count : 0L;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Collection<String>> keys(String pattern) {
        return Mono.fromCallable(() -> {
                    Set<String> keys = redisTemplate.keys(pattern);
                    return keys != null ? (Collection<String>) keys : java.util.Collections.<String>emptySet();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}

