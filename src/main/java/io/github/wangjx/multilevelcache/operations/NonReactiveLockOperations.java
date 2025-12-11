package io.github.wangjx.multilevelcache.operations;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

/**
 * 普通 Redis 分布式锁操作实现（包装为 Mono）
 * @author wangjx
 */
public class NonReactiveLockOperations implements LockOperations {

    private final StringRedisTemplate stringRedisTemplate;

    public NonReactiveLockOperations(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Mono<Boolean> setIfAbsent(String key, String value, Duration expire) {
        return Mono.fromCallable(() -> {
                    Boolean result = stringRedisTemplate.opsForValue()
                            .setIfAbsent(key, value, expire);
                    return Boolean.TRUE.equals(result);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> executeScript(String script, List<String> keys, String... args) {
        return Mono.fromCallable(() -> {
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
                    Long result = stringRedisTemplate.execute(redisScript, keys, (Object[]) args);
                    return result != null ? result : 0L;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> hasKey(String key) {
        return Mono.fromCallable(() -> Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)))
                .subscribeOn(Schedulers.boundedElastic());
    }
}

