package io.github.wangjx.multilevelcache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import io.github.wangjx.multilevelcache.CacheInvalidationService;
import io.github.wangjx.multilevelcache.LockManager;
import io.github.wangjx.multilevelcache.MultiLevelCacheManager;
import io.github.wangjx.multilevelcache.invalidation.ReactiveCacheInvalidationService;
import io.github.wangjx.multilevelcache.operations.CacheOperations;
import io.github.wangjx.multilevelcache.operations.ReactiveCacheOperations;
import io.github.wangjx.multilevelcache.properties.MultiLevelCacheProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reactive Redis 多级缓存自动配置类
 * @author wangjx
 */
@AutoConfiguration
@ConditionalOnClass({ReactiveRedisTemplate.class, ReactiveRedisConnectionFactory.class, CacheManager.class})
@ConditionalOnBean(ReactiveRedisConnectionFactory.class)
@ConditionalOnMissingBean(name = "redisTemplate") // 只在没有普通 Redis 时才启用
@EnableConfigurationProperties(MultiLevelCacheProperties.class)
public class MultiLevelCacheReactiveAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheReactiveAutoConfiguration.class);

    /**
     * 配置Reactive Redis Template（用于缓存值）
     */
    @Bean
    @ConditionalOnMissingBean(name = "reactiveRedisTemplate")
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        
        log.debug("Creating ReactiveRedisTemplate for multi-level cache with type information support");
        
        // Key序列化器：使用String
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        
        // 配置 ObjectMapper 以支持类型信息，防止反序列化时变成 LinkedHashMap
        // 创建专用的 ObjectMapper 用于 Redis 序列化
        ObjectMapper redisObjectMapper = objectMapper.copy();
        
        // 配置多态类型验证器，允许所有 Object 类型的子类
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        
        // 启用默认类型信息，这样序列化时会添加 @class 字段
        // 反序列化时可以根据 @class 字段恢复正确的类型
        redisObjectMapper.activateDefaultTyping(
                ptv,
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );
        
        // Value序列化器：使用GenericJackson2JsonRedisSerializer（自动支持类型信息）
        // 这个序列化器会在 JSON 中添加 @class 字段，确保反序列化时能恢复正确的类型
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        
        // 配置序列化上下文
        RedisSerializationContext.RedisSerializationContextBuilder<String, Object> builder =
                RedisSerializationContext.newSerializationContext(keySerializer);
        
        RedisSerializationContext<String, Object> context = builder
                .value(valueSerializer)
                .hashKey(keySerializer)
                .hashValue(valueSerializer)
                .build();
        
        log.debug("ReactiveRedisTemplate created successfully with GenericJackson2JsonRedisSerializer");
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    /**
     * 配置Reactive String Redis Template（用于分布式锁）
     */
    @Bean
    @ConditionalOnMissingBean(name = "reactiveStringRedisTemplate")
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }

    /**
     * 配置缓存操作接口（Reactive 实现）
     */
    @Bean
    @ConditionalOnMissingBean(CacheOperations.class)
    public CacheOperations cacheOperations(ReactiveRedisTemplate<String, Object> reactiveRedisTemplate) {
        log.debug("Creating ReactiveCacheOperations");
        return new ReactiveCacheOperations(reactiveRedisTemplate);
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
     * 配置分布式锁管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public LockManager lockManager(ReactiveStringRedisTemplate reactiveStringRedisTemplate,
                                  MultiLevelCacheProperties properties) {
        // 默认锁过期时间：30秒
        // 默认等待时间：5秒
        return new LockManager(
                reactiveStringRedisTemplate,
                30,
                java.util.concurrent.TimeUnit.SECONDS,
                5,
                java.util.concurrent.TimeUnit.SECONDS
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheInvalidationService cacheInvalidationService(
            ReactiveStringRedisTemplate reactiveStringRedisTemplate,
            ObjectMapper objectMapper,
            MultiLevelCacheProperties properties) {
        log.debug("Creating ReactiveCacheInvalidationService with channel: {}", properties.getInvalidationChannel());
        return new ReactiveCacheInvalidationService(
                reactiveStringRedisTemplate, 
                objectMapper, 
                properties.getInvalidationChannel());
    }
}

