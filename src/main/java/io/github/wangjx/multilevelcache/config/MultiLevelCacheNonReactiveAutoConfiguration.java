package io.github.wangjx.multilevelcache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import io.github.wangjx.multilevelcache.CacheInvalidationService;
import io.github.wangjx.multilevelcache.LockManager;
import io.github.wangjx.multilevelcache.MultiLevelCacheManager;
import io.github.wangjx.multilevelcache.invalidation.NonReactiveCacheInvalidationService;
import io.github.wangjx.multilevelcache.operations.CacheOperations;
import io.github.wangjx.multilevelcache.operations.LockOperations;
import io.github.wangjx.multilevelcache.operations.NonReactiveCacheOperations;
import io.github.wangjx.multilevelcache.operations.NonReactiveLockOperations;
import io.github.wangjx.multilevelcache.properties.MultiLevelCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 普通 Redis 多级缓存自动配置类
 * @author wangjx
 */
@AutoConfiguration
@ConditionalOnClass({RedisTemplate.class, RedisConnectionFactory.class, CacheManager.class})
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnMissingBean(name = "reactiveRedisTemplate") // 只在没有 Reactive Redis 时才启用
@EnableConfigurationProperties(MultiLevelCacheProperties.class)
public class MultiLevelCacheNonReactiveAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheNonReactiveAutoConfiguration.class);

    /**
     * 配置 Redis Template（用于缓存值）
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        
        log.debug("Creating RedisTemplate for multi-level cache with type information support");
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Key序列化器：使用String
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        
        // 配置 ObjectMapper 以支持类型信息，防止反序列化时变成 LinkedHashMap
        ObjectMapper redisObjectMapper = objectMapper.copy();
        
        // 配置多态类型验证器，允许所有 Object 类型的子类
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        
        // 启用默认类型信息
        redisObjectMapper.activateDefaultTyping(
                ptv,
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );
        
        // Value序列化器：使用GenericJackson2JsonRedisSerializer
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        
        template.afterPropertiesSet();
        log.debug("RedisTemplate created successfully with GenericJackson2JsonRedisSerializer");
        return template;
    }

    /**
     * 配置 String Redis Template（用于分布式锁和 Pub/Sub）
     */
    @Bean
    @ConditionalOnMissingBean(name = "stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 配置 Redis 消息监听容器（用于 Pub/Sub）
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        log.debug("RedisMessageListenerContainer created");
        return container;
    }

    /**
     * 配置缓存操作接口（普通 Redis 实现）
     */
    @Bean
    @ConditionalOnMissingBean(CacheOperations.class)
    public CacheOperations cacheOperations(RedisTemplate<String, Object> redisTemplate) {
        log.debug("Creating NonReactiveCacheOperations");
        return new NonReactiveCacheOperations(redisTemplate);
    }

    /**
     * 配置多级缓存管理器（作为Spring Cache的主缓存管理器）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(CacheOperations cacheOperations,
                                     MultiLevelCacheProperties properties,
                                     CacheInvalidationService cacheInvalidationService) {
        log.debug("Creating MultiLevelCacheManager with properties: localMaxSize={}, localExpireSeconds={}, redisExpireSeconds={}, redisTimeout={}",
                properties.getLocalCacheMaxSize(), properties.getLocalCacheExpireSeconds(),
                properties.getRedisCacheExpireSeconds(), properties.getRedisTimeout());
        return new MultiLevelCacheManager(cacheOperations, properties, cacheInvalidationService);
    }

    /**
     * 配置锁操作接口（普通 Redis 实现）
     */
    @Bean
    @ConditionalOnMissingBean(LockOperations.class)
    public LockOperations lockOperations(StringRedisTemplate stringRedisTemplate) {
        log.debug("Creating NonReactiveLockOperations");
        return new NonReactiveLockOperations(stringRedisTemplate);
    }

    /**
     * 配置分布式锁管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public LockManager lockManager(LockOperations lockOperations,
                                  MultiLevelCacheProperties properties) {
        // 默认锁过期时间：30秒
        // 默认等待时间：5秒
        log.debug("Creating LockManager with NonReactiveLockOperations");
        return new LockManager(
                lockOperations,
                30,
                java.util.concurrent.TimeUnit.SECONDS,
                5,
                java.util.concurrent.TimeUnit.SECONDS
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheInvalidationService cacheInvalidationService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            MultiLevelCacheProperties properties,
            RedisMessageListenerContainer messageListenerContainer) {
        log.debug("Creating NonReactiveCacheInvalidationService with channel: {}", properties.getInvalidationChannel());
        return new NonReactiveCacheInvalidationService(
                stringRedisTemplate, 
                objectMapper, 
                properties.getInvalidationChannel(),
                messageListenerContainer);
    }
}

