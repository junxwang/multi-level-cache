package io.github.wangjx.multilevelcache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.SimpleValueWrapper;
import io.github.wangjx.multilevelcache.operations.CacheOperations;
import io.github.wangjx.multilevelcache.properties.MultiLevelCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 多级缓存管理器
 * 第一级：本地Caffeine缓存（L1）
 * 第二级：Reactive Redis缓存（L2）
 * @author wangjx
 */
public class MultiLevelCacheManager extends AbstractCacheManager {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheManager.class);

    private final CacheOperations cacheOperations;
    private final MultiLevelCacheProperties properties;
    private final CacheInvalidationService cacheInvalidationService;

    // 本地缓存Map，key为cacheName，value为Caffeine Cache实例
    private final ConcurrentHashMap<String, Cache> localCaches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MultiLevelCache> cacheWrappers = new ConcurrentHashMap<>();

    public MultiLevelCacheManager(CacheOperations cacheOperations,
                                  MultiLevelCacheProperties properties,
                                  CacheInvalidationService cacheInvalidationService) {
        this.cacheOperations = cacheOperations;
        this.properties = properties;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Override
    protected Collection<? extends org.springframework.cache.Cache> loadCaches() {
        return Collections.emptyList();
    }

    @Override
    public org.springframework.cache.Cache getCache(String name) {
        return cacheWrappers.computeIfAbsent(name, cacheName -> {
            log.debug("Creating new multi-level cache: {}", cacheName);
            // 确保本地缓存已创建
            Cache localCache = localCaches.computeIfAbsent(cacheName, ignored -> {
                log.debug("Creating local Caffeine cache: {}, maxSize={}, expireSeconds={}", 
                        cacheName, properties.getLocalCacheMaxSize(), properties.getLocalCacheExpireSeconds());
                AsyncCache<Object, @Nullable Object> asyncCache = Caffeine.newBuilder()
                        .maximumSize(properties.getLocalCacheMaxSize())
                        .expireAfterWrite(properties.getLocalCacheExpireSeconds(), TimeUnit.SECONDS)
                        .recordStats()
                        .buildAsync();
                return new CaffeineCache(cacheName, asyncCache, true);
            });

            MultiLevelCache cache = new MultiLevelCache(cacheName, localCache, cacheOperations, properties, cacheInvalidationService);
            cacheInvalidationService.registerCache(cache);
            log.debug("Multi-level cache created and registered: {}", cacheName);
            return cache;
        });
    }

    /**
     * 多级缓存实现
     */
    public static class MultiLevelCache extends AbstractValueAdaptingCache {

        private static final Logger log = LoggerFactory.getLogger(MultiLevelCache.class);

        private final String name;
        private final Cache localCache;
        private final CacheOperations cacheOperations;
        private final MultiLevelCacheProperties properties;
        private final CacheInvalidationService cacheInvalidationService;
        // 读写锁：读操作可以并发，写操作互斥
        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
        private final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();

        public MultiLevelCache(String name,
                               Cache localCache,
                               CacheOperations cacheOperations,
                               MultiLevelCacheProperties properties,
                               CacheInvalidationService cacheInvalidationService) {
            super(true);
            this.name = name;
            this.localCache = localCache;
            this.cacheOperations = cacheOperations;
            this.properties = properties;
            this.cacheInvalidationService = cacheInvalidationService;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        public ValueWrapper get(Object key) {
            return getReactive(key)
                    .block(Duration.ofSeconds(properties.getRedisTimeout()));
        }

        @Override
        public CompletableFuture<ValueWrapper> retrieve(Object key) {
            return getReactive(key).toFuture();
        }

        public Mono<ValueWrapper> getReactive(Object key) {
            return Mono.defer(() -> {
                String redisKey = buildRedisKey(key);
                // 读操作使用读锁（可以并发）
                readLock.lock();
                try {
                    // L1: 先从本地缓存获取
                    Object value = getLocalCacheValue(key);
                    if (value != null) {
                        log.debug("Cache HIT (L1): cacheName={}, key={}, redisKey={}", name, key, redisKey);
                        return Mono.just(new SimpleValueWrapper(value));
                    }
                } finally {
                    readLock.unlock();
                }

                log.debug("Cache MISS (L1), querying Redis (L2): cacheName={}, key={}, redisKey={}", name, key, redisKey);
                // L2: 从Redis获取（完全异步）
                return cacheOperations.get(redisKey)
                        .timeout(Duration.ofSeconds(properties.getRedisTimeout()))
                        .flatMap(redisValue -> {
                            if (redisValue == null) {
                                log.debug("Cache MISS (L2): cacheName={}, key={}, redisKey={}", name, key, redisKey);
                            } else {
                                log.debug("Cache HIT (L2): cacheName={}, key={}, redisKey={}, valueType={}", 
                                        name, key, redisKey, redisValue.getClass().getSimpleName());
                            }
                            // redisValue 已经通过 GenericJackson2JsonRedisSerializer 反序列化，类型应该正确
                            return Mono.fromCallable(() -> {
                                writeLock.lock();
                                try {
                                    // 双重检查：可能在获取写锁期间，其他线程已经写入了
                                    Object existingValue = getLocalCacheValue(key);
                                    if (existingValue == null && redisValue != null) {
                                        // 将反序列化后的值存入本地缓存
                                        log.debug("Loading value from L2 to L1: cacheName={}, key={}", name, key);
                                        localCache.put(key, redisValue);
                                        return new SimpleValueWrapper(redisValue);
                                    }
                                    if (existingValue != null) {
                                        log.debug("Value already loaded to L1 by another thread: cacheName={}, key={}", name, key);
                                    }
                                    return new SimpleValueWrapper(existingValue);
                                } finally {
                                    writeLock.unlock();
                                }
                            }).subscribeOn(Schedulers.boundedElastic());
                        })
                        .cast(ValueWrapper.class);
            });
        }


        public Mono<Object> getObject(Object key) {

            // 读操作使用读锁（可以并发）
            readLock.lock();
            try {
                // L1: 先从本地缓存获取
                Object value = getLocalCacheValue(key);
                if (value != null) {
                    return Mono.just(value);
                }
            } finally {
                readLock.unlock();
            }

            // L2: 从Redis获取（完全异步）
            String redisKey = buildRedisKey(key);
            return cacheOperations.get(redisKey)
                    .timeout(Duration.ofSeconds(properties.getRedisTimeout()))
                    .flatMap(redisValue -> {
                        // redisValue 已经通过 GenericJackson2JsonRedisSerializer 反序列化，类型应该正确
                        return Mono.fromCallable(() -> {
                            writeLock.lock();
                            try {
                                // 双重检查：可能在获取写锁期间，其他线程已经写入了
                                Object existingValue = getLocalCacheValue(key);
                                if (existingValue == null) {
                                    // 将反序列化后的值存入本地缓存
                                    localCache.put(key, redisValue);
                                    return new SimpleValueWrapper(redisValue);
                                }
                                return new SimpleValueWrapper(existingValue);
                            } finally {
                                writeLock.unlock();
                            }
                        }).subscribeOn(Schedulers.boundedElastic());
                    })
                    .cast(Object.class);

        }


        @Override
        public <T> T get(Object key, Class<T> type) {
            ValueWrapper wrapper = get(key);
            if (wrapper != null) {
                Object value = wrapper.get();
                if (type != null && !type.isInstance(value)) {
                    throw new IllegalStateException(
                            "Cached value is not of required type [" + type.getName() + "]: " + value);
                }
                return (T) value;
            }
            return null;
        }

        @Override
        protected Object lookup(Object key) {
           return getObject(key);
        }

        @Override
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            ValueWrapper wrapper = get(key);
            if (wrapper != null) {
                return (T) wrapper.get();
            }

            // 如果缓存中没有，执行valueLoader
            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            if (value == null) {
                log.debug("Putting null value, evicting instead: cacheName={}, key={}", name, key);
                evict(key);
                return;
            }

            String redisKey = buildRedisKey(key);
            log.debug("Putting value to cache: cacheName={}, key={}, redisKey={}, valueType={}, expireSeconds={}", 
                    name, key, redisKey, value.getClass().getSimpleName(), properties.getRedisCacheExpireSeconds());

            // 写操作使用写锁（互斥）
            writeLock.lock();
            try {
                // L1: 写入本地缓存
                localCache.put(key, value);
                log.debug("Value written to L1: cacheName={}, key={}", name, key);
            } finally {
                writeLock.unlock();
            }

            // L2: 写入Redis（异步）
            cacheOperations.set(redisKey, value, Duration.ofSeconds(properties.getRedisCacheExpireSeconds()))
                    .doOnSuccess(success -> {
                        log.debug("Value written to L2 (Redis): cacheName={}, key={}, redisKey={}", name, key, redisKey);
                        cacheInvalidationService.publishEvict(name, key);
                    })
                    .doOnError(error -> log.warn("Failed to write value to L2 (Redis): cacheName={}, key={}, redisKey={}", 
                            name, key, redisKey, error))
                    .subscribe();
        }

        @Override
        public void evict(Object key) {
            String redisKey = buildRedisKey(key);
            log.debug("Evicting key from cache: cacheName={}, key={}, redisKey={}", name, key, redisKey);

            // 写操作使用写锁（互斥）
            writeLock.lock();
            try {
                // L1: 清除本地缓存
                localCache.evict(key);
                log.debug("Key evicted from L1: cacheName={}, key={}", name, key);
            } finally {
                writeLock.unlock();
            }

            // L2: 清除Redis缓存（异步）
            cacheOperations.delete(redisKey)
                    .doOnSuccess(success -> {
                        log.debug("Key evicted from L2 (Redis): cacheName={}, key={}, redisKey={}", name, key, redisKey);
                        cacheInvalidationService.publishEvict(name, key);
                    })
                    .doOnError(error -> log.warn("Failed to evict key from L2 (Redis): cacheName={}, key={}, redisKey={}", 
                            name, key, redisKey, error))
                    .subscribe();
        }

        @Override
        public void clear() {
            log.debug("Clearing all cache: cacheName={}", name);

            // 写操作使用写锁（互斥）
            writeLock.lock();
            try {
                // L1: 清除本地缓存
                localCache.invalidate();
                log.debug("L1 cache cleared: cacheName={}", name);
            } finally {
                writeLock.unlock();
            }

            // L2: 清除Redis缓存（异步，需要根据cache name pattern删除）
            // 注意：这里只清除当前cache的key，实际使用中可能需要更精确的key pattern
            String pattern = name + ":*";
            log.debug("Clearing L2 cache (Redis) with pattern: {}", pattern);
            cacheOperations.keys(pattern)
                    .flatMap(keys -> {
                        if (keys.isEmpty()) {
                            log.debug("No keys found to delete in L2: cacheName={}, pattern={}", name, pattern);
                            return Mono.just(0L);
                        }
                        log.debug("Deleting {} keys from L2: cacheName={}, pattern={}", keys.size(), name, pattern);
                        return cacheOperations.delete(keys);
                    })
                    .doOnSuccess(count -> {
                        log.debug("L2 cache cleared: cacheName={}, deletedKeys={}", name, count);
                        cacheInvalidationService.publishClear(name);
                    })
                    .doOnError(error -> log.warn("Failed to clear L2 cache: cacheName={}, pattern={}", 
                            name, pattern, error))
                    .subscribe();
        }

        /**
         * 构建Redis key
         */
        private String buildRedisKey(Object key) {
            return name + ":" + key.toString();
        }

        private Object getLocalCacheValue(Object key) {
            Cache.ValueWrapper wrapper = localCache.get(key);
            return wrapper != null ? wrapper.get() : null;
        }


        void handleRemoteEvict(Object key) {
            if (key == null) {
                log.debug("Received remote evict with null key, ignoring: cacheName={}", name);
                return;
            }
            log.debug("Handling remote evict: cacheName={}, key={}", name, key);
            writeLock.lock();
            try {
                localCache.evict(key);
                log.debug("Remote evict completed: cacheName={}, key={}", name, key);
            } finally {
                writeLock.unlock();
            }
        }

        void handleRemoteClear() {
            log.debug("Handling remote clear: cacheName={}", name);
            writeLock.lock();
            try {
                localCache.invalidate();
                log.debug("Remote clear completed: cacheName={}", name);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * 获取读写锁状态信息（用于监控和调试）
         */
        public LockInfo getLockInfo() {
            return new LockInfo(
                    readWriteLock.getReadLockCount(),
                    readWriteLock.getWriteHoldCount(),
                    readWriteLock.isWriteLocked(),
                    readWriteLock.getQueueLength()
            );
        }

        /**
         * 锁信息类
         */
        public static class LockInfo {
            private final int readLockCount;
            private final int writeHoldCount;
            private final boolean writeLocked;
            private final int queueLength;

            public LockInfo(int readLockCount, int writeHoldCount, boolean writeLocked, int queueLength) {
                this.readLockCount = readLockCount;
                this.writeHoldCount = writeHoldCount;
                this.writeLocked = writeLocked;
                this.queueLength = queueLength;
            }

            public int getReadLockCount() {
                return readLockCount;
            }

            public int getWriteHoldCount() {
                return writeHoldCount;
            }

            public boolean isWriteLocked() {
                return writeLocked;
            }

            public int getQueueLength() {
                return queueLength;
            }

            @Override
            public String toString() {
                return "LockInfo{" +
                        "readLockCount=" + readLockCount +
                        ", writeHoldCount=" + writeHoldCount +
                        ", writeLocked=" + writeLocked +
                        ", queueLength=" + queueLength +
                        '}';
            }
        }
    }
}

