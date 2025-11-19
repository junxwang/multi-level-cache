package io.github.wangjx.multilevelcache.invalidation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wangjx.multilevelcache.CacheInvalidationService;
import io.github.wangjx.multilevelcache.model.CacheInvalidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 普通 Redis 缓存失效服务实现
 * @author wangjx
 */
public class NonReactiveCacheInvalidationService extends CacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(NonReactiveCacheInvalidationService.class);
    private final RedisMessageListenerContainer messageListenerContainer;
    private final StringRedisTemplate stringRedisTemplate;

    public NonReactiveCacheInvalidationService(StringRedisTemplate stringRedisTemplate,
                                               ObjectMapper objectMapper,
                                               String channel,
                                               RedisMessageListenerContainer messageListenerContainer) {
        super(objectMapper, channel);
        this.stringRedisTemplate = stringRedisTemplate;
        this.messageListenerContainer = messageListenerContainer;
        
        log.debug("Initializing NonReactiveCacheInvalidationService with channel: {}, instanceId: {}", 
                channel, getInstanceId());

        // 使用 RedisMessageListenerContainer 订阅频道
        messageListenerContainer.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String payload = new String(message.getBody());
                handleIncomingPayloadSync(payload);
            }
        }, new ChannelTopic(channel));
        
        messageListenerContainer.start();
        log.debug("Subscribed to cache invalidation channel: {}", channel);
    }

    private void handleIncomingPayloadSync(String payload) {
        log.debug("Received cache invalidation message: {}", payload);
        try {
            CacheInvalidationMessage message = objectMapper.readValue(payload, CacheInvalidationMessage.class);
            handleMessage(message);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cache invalidation message: {}", payload, e);
        }
    }

    @Override
    protected void publishMessage(String payload) {
        Long count = stringRedisTemplate.convertAndSend(getChannel(), payload);
        log.debug("Cache invalidation message sent successfully, subscribers: {}", count);
    }

    @Override
    public void destroy() {
        if (messageListenerContainer != null && messageListenerContainer.isRunning()) {
            log.debug("Stopping Redis message listener container");
            messageListenerContainer.stop();
        }
        super.destroy();
    }
}

