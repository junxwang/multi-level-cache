package io.github.wangjx.multilevelcache;

import io.github.wangjx.multilevelcache.operations.LockOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的分布式锁实现（支持 Reactive 和普通 Redis）
 * @author wangjx
 */
public class DistributedLock {
    
    private final LockOperations lockOperations;
    private final String lockKey;
    private final String lockValue;
    private final long expireTime;
    private final TimeUnit timeUnit;
    
    private DistributedLock(LockOperations lockOperations,
                           String lockKey,
                           long expireTime,
                           TimeUnit timeUnit) {
        this.lockOperations = lockOperations;
        this.lockKey = "lock:" + lockKey;
        this.lockValue = UUID.randomUUID().toString();
        this.expireTime = expireTime;
        this.timeUnit = timeUnit;
    }

    /**
     * 创建分布式锁实例
     * 
     * @param lockOperations 锁操作接口
     * @param lockKey 锁的key
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @return 分布式锁实例
     */
    public static DistributedLock create(LockOperations lockOperations,
                                        String lockKey,
                                        long expireTime,
                                        TimeUnit timeUnit) {
        return new DistributedLock(lockOperations, lockKey, expireTime, timeUnit);
    }

    /**
     * 尝试获取锁（非阻塞）
     * 
     * @return Mono<Boolean> true表示获取成功，false表示获取失败
     */
    public Mono<Boolean> tryLock() {
        return lockOperations.setIfAbsent(
                lockKey, 
                lockValue, 
                Duration.ofMillis(timeUnit.toMillis(expireTime))
        );
    }

    /**
     * 尝试获取锁（阻塞，带超时）
     * 
     * @param waitTime 等待时间
     * @param waitTimeUnit 等待时间单位
     * @return Mono<Boolean> true表示获取成功，false表示超时
     */
    public Mono<Boolean> tryLock(long waitTime, TimeUnit waitTimeUnit) {
        long waitMillis = waitTimeUnit.toMillis(waitTime);
        long startTime = System.currentTimeMillis();
        long retryInterval = 100; // 重试间隔100ms
        
        return tryLockRecursive(waitMillis, startTime, retryInterval);
    }
    
    private Mono<Boolean> tryLockRecursive(long waitMillis, long startTime, long retryInterval) {
        return tryLock()
                .flatMap(acquired -> {
                    if (acquired) {
                        return Mono.just(true);
                    }
                    
                    long elapsed = System.currentTimeMillis() - startTime;
                    // 如果未获取到锁且未超时，继续重试
                    if (elapsed < waitMillis) {
                        return Mono.delay(Duration.ofMillis(retryInterval))
                                .then(tryLockRecursive(waitMillis, startTime, retryInterval));
                    }
                    
                    return Mono.just(false);
                });
    }

    /**
     * 释放锁
     *
     * @return Mono<Long> 返回删除的key数量，1表示释放成功，0表示释放失败
     */
    public Mono<Long> unlock() {
        // 使用Lua脚本确保原子性：只有锁的持有者才能释放锁
        String luaScript = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
        
        return lockOperations.executeScript(
                luaScript, 
                Collections.singletonList(lockKey), 
                lockValue
        );
    }

    /**
     * 检查锁是否存在
     * 
     * @return Mono<Boolean> true表示锁存在
     */
    public Mono<Boolean> isLocked() {
        return lockOperations.hasKey(lockKey);
    }

    /**
     * 获取锁的key
     */
    public String getLockKey() {
        return lockKey;
    }

    /**
     * 获取锁的值
     */
    public String getLockValue() {
        return lockValue;
    }
}

