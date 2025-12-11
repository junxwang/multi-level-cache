package io.github.wangjx.multilevelcache.operations;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Reactive Redis 分布式锁操作实现
 * @author wangjx
 */
public class ReactiveLockOperations implements LockOperations {

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    public ReactiveLockOperations(ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
    }

    @Override
    public Mono<Boolean> setIfAbsent(String key, String value, Duration expire) {
        return reactiveStringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, expire)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> executeScript(String script, List<String> keys, String... args) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        return reactiveStringRedisTemplate.execute(redisScript, keys, (Object[]) args)
                .next()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Boolean> hasKey(String key) {
        return reactiveStringRedisTemplate.hasKey(key);
    }
}

