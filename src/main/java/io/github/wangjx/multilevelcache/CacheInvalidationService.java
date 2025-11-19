package io.github.wangjx.multilevelcache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import io.github.wangjx.multilevelcache.model.CacheInvalidationMessage;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责通过Redis Pub/Sub广播缓存失效事件，并在收到消息时清理本地一级缓存。
 * 抽象基类，具体实现由子类提供。
 * @author wangjx
 */
public abstract class CacheInvalidationService implements DisposableBean {

    protected static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);

    protected final ObjectMapper objectMapper;
    protected final String channel;
    protected final String instanceId = UUID.randomUUID().toString();
    protected final Map<String, MultiLevelCacheManager.MultiLevelCache> caches = new ConcurrentHashMap<>();

    protected CacheInvalidationService(ObjectMapper objectMapper, String channel) {
        this.objectMapper = objectMapper;
        this.channel = channel;
    }

    protected String getChannel() {
        return channel;
    }

    protected String getInstanceId() {
        return instanceId;
    }

    public void registerCache(MultiLevelCacheManager.MultiLevelCache cache) {
        caches.put(cache.getName(), cache);
        log.debug("Registered cache: {} for invalidation service", cache.getName());
    }

    public void publishEvict(String cacheName, Object key) {
        if (key == null) {
            return;
        }
        log.debug("Publishing cache evict message: cacheName={}, key={}", cacheName, key);
        CacheInvalidationMessage message = new CacheInvalidationMessage(
                instanceId,
                cacheName,
                CacheInvalidationMessage.Action.EVICT,
                key.toString());
        publish(message);
    }

    public void publishClear(String cacheName) {
        log.debug("Publishing cache clear message: cacheName={}", cacheName);
        CacheInvalidationMessage message = new CacheInvalidationMessage(
                instanceId,
                cacheName,
                CacheInvalidationMessage.Action.CLEAR,
                null);
        publish(message);
    }

    protected void publish(CacheInvalidationMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            log.debug("Sending cache invalidation message to channel {}: {}", channel, payload);
            publishMessage(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize cache invalidation message: {}", message, e);
        }
    }

    /**
     * 子类实现具体的消息发布逻辑
     */
    protected abstract void publishMessage(String payload);

    protected Mono<Void> handleIncomingPayload(String payload) {
        log.debug("Received cache invalidation message: {}", payload);
        return Mono.fromCallable(() -> objectMapper.readValue(payload, CacheInvalidationMessage.class))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(this::handleMessage)
                .doOnError(error -> log.warn("Failed to deserialize cache invalidation message: {}", payload, error))
                .then();
    }

    protected void handleMessage(CacheInvalidationMessage message) {
        if (message == null) {
            log.debug("Received null cache invalidation message, ignoring");
            return;
        }
        if (Objects.equals(instanceId, message.getInstanceId())) {
            log.debug("Received cache invalidation message from self (instanceId: {}), ignoring", instanceId);
            return;
        }

        log.debug("Processing cache invalidation message: cacheName={}, action={}, key={}, fromInstanceId={}",
                message.getCacheName(), message.getAction(), message.getKey(), message.getInstanceId());

        MultiLevelCacheManager.MultiLevelCache cache = caches.get(message.getCacheName());
        if (cache == null) {
            log.debug("Cache not found for invalidation: {}, available caches: {}", 
                    message.getCacheName(), caches.keySet());
            return;
        }

        if (message.getAction() == CacheInvalidationMessage.Action.CLEAR) {
            log.debug("Clearing local cache: {}", message.getCacheName());
            cache.handleRemoteClear();
        } else if (message.getAction() == CacheInvalidationMessage.Action.EVICT) {
            log.debug("Evicting key from local cache: cacheName={}, key={}", 
                    message.getCacheName(), message.getKey());
            cache.handleRemoteEvict(message.getKey());
        }
    }

    @Override
    public void destroy() {
        // 清理资源，例如取消订阅、关闭连接等
        log.info("Destroying CacheInvalidationService");

    }
}


