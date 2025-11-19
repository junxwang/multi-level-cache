package io.github.wangjx.multilevelcache.invalidation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wangjx.multilevelcache.CacheInvalidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import reactor.core.Disposable;

/**
 * Reactive Redis 缓存失效服务实现
 * @author wangjx
 */
public class ReactiveCacheInvalidationService extends CacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(ReactiveCacheInvalidationService.class);
    private final Disposable subscription;

    private final ReactiveStringRedisTemplate stringRedisTemplate;

    public ReactiveCacheInvalidationService(ReactiveStringRedisTemplate stringRedisTemplate,
                                            ObjectMapper objectMapper,
                                            String channel) {
        super(objectMapper, channel);
        this.stringRedisTemplate = stringRedisTemplate;
        
        log.debug("Initializing ReactiveCacheInvalidationService with channel: {}, instanceId: {}", 
                channel, getInstanceId());

        // 使用 ReactiveStringRedisTemplate 的 listenTo 方法订阅频道
        this.subscription = stringRedisTemplate.listenTo(ChannelTopic.of(channel))
                .map(ReactiveSubscription.Message::getMessage)
                .flatMap(this::handleIncomingPayload)
                .onErrorContinue((throwable, payload) ->
                        log.warn("Failed to process cache invalidation message", throwable))
                .doOnSubscribe(sub -> log.debug("Subscribed to cache invalidation channel: {}", channel))
                .subscribe();
    }

    @Override
    protected void publishMessage(String payload) {
        stringRedisTemplate.convertAndSend(getChannel(), payload)
                .doOnSuccess(count -> log.debug("Cache invalidation message sent successfully, subscribers: {}", count))
                .doOnError(error -> log.warn("Failed to send cache invalidation message", error))
                .subscribe();
    }

    @Override
    public void destroy() {
        if (subscription != null && !subscription.isDisposed()) {
            log.debug("Unsubscribing from cache invalidation channel: {}", getChannel());
            subscription.dispose();
        }
        super.destroy();
    }
}

