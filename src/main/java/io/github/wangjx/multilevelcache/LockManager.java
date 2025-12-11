package io.github.wangjx.multilevelcache;

import io.github.wangjx.multilevelcache.operations.LockOperations;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 分布式锁管理器
 * 提供便捷的锁操作方法（支持 Reactive 和普通 Redis）
 * @author wangjx
 */
public class LockManager {
    
    private final LockOperations lockOperations;
    private final long defaultExpireTime;
    private final TimeUnit defaultTimeUnit;
    private final long defaultWaitTime;
    private final TimeUnit defaultWaitTimeUnit;

    public LockManager(LockOperations lockOperations,
                      long defaultExpireTime,
                      TimeUnit defaultTimeUnit,
                      long defaultWaitTime,
                      TimeUnit defaultWaitTimeUnit) {
        this.lockOperations = lockOperations;
        this.defaultExpireTime = defaultExpireTime;
        this.defaultTimeUnit = defaultTimeUnit;
        this.defaultWaitTime = defaultWaitTime;
        this.defaultWaitTimeUnit = defaultWaitTimeUnit;
    }

    /**
     * 使用锁执行操作（自动获取和释放锁）
     * 
     * @param lockKey 锁的key
     * @param action 要执行的操作
     * @return Mono<T> 操作结果
     */
    public <T> Mono<T> executeWithLock(String lockKey, Function<DistributedLock, Mono<T>> action) {
        return executeWithLock(lockKey, defaultExpireTime, defaultTimeUnit, 
                              defaultWaitTime, defaultWaitTimeUnit, action);
    }

    /**
     * 使用锁执行操作（自动获取和释放锁）
     * 
     * @param lockKey 锁的key
     * @param expireTime 锁的过期时间
     * @param timeUnit 时间单位
     * @param action 要执行的操作
     * @return Mono<T> 操作结果
     */
    public <T> Mono<T> executeWithLock(String lockKey,
                                       long expireTime,
                                       TimeUnit timeUnit,
                                       Function<DistributedLock, Mono<T>> action) {
        return executeWithLock(lockKey, expireTime, timeUnit, 
                              defaultWaitTime, defaultWaitTimeUnit, action);
    }

    /**
     * 使用锁执行操作（自动获取和释放锁）
     * 
     * @param lockKey 锁的key
     * @param expireTime 锁的过期时间
     * @param timeUnit 时间单位
     * @param waitTime 等待获取锁的时间
     * @param waitTimeUnit 等待时间单位
     * @param action 要执行的操作
     * @return Mono<T> 操作结果
     */
    public <T> Mono<T> executeWithLock(String lockKey,
                                       long expireTime,
                                       TimeUnit timeUnit,
                                       long waitTime,
                                       TimeUnit waitTimeUnit,
                                       Function<DistributedLock, Mono<T>> action) {
        DistributedLock lock = DistributedLock.create(lockOperations, lockKey, expireTime, timeUnit);
        
        return lock.tryLock(waitTime, waitTimeUnit)
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.error(new RuntimeException("Failed to acquire lock: " + lockKey));
                    }
                    
                    return action.apply(lock)
                            .doFinally(signalType -> {
                                // 无论成功还是失败，都要释放锁
                                lock.unlock()
                                        .doOnError(error -> 
                                                org.slf4j.LoggerFactory.getLogger(LockManager.class)
                                                        .warn("Failed to release lock: " + lockKey, error))
                                        .subscribe();
                            });
                });
    }

    /**
     * 创建锁实例
     * 
     * @param lockKey 锁的key
     * @return 分布式锁实例
     */
    public DistributedLock createLock(String lockKey) {
        return DistributedLock.create(lockOperations, lockKey, defaultExpireTime, defaultTimeUnit);
    }

    /**
     * 创建锁实例（自定义过期时间）
     * 
     * @param lockKey 锁的key
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @return 分布式锁实例
     */
    public DistributedLock createLock(String lockKey, long expireTime, TimeUnit timeUnit) {
        return DistributedLock.create(lockOperations, lockKey, expireTime, timeUnit);
    }
}

