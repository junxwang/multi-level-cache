package io.github.wangjx.multilevelcache.operations;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 统一的分布式锁操作接口，支持 Reactive 和普通 Redis
 * @author wangjx
 */
public interface LockOperations {
    
    /**
     * 设置 key-value，仅当 key 不存在时
     * @param key Redis key
     * @param value 值
     * @param expire 过期时间
     * @return Mono<Boolean> true 表示设置成功，false 表示 key 已存在
     */
    Mono<Boolean> setIfAbsent(String key, String value, Duration expire);
    
    /**
     * 执行 Lua 脚本
     * @param script Lua 脚本
     * @param keys 键列表
     * @param args 参数列表
     * @return Mono<Long> 脚本执行结果
     */
    Mono<Long> executeScript(String script, List<String> keys, String... args);
    
    /**
     * 检查 key 是否存在
     * @param key Redis key
     * @return Mono<Boolean> true 表示 key 存在
     */
    Mono<Boolean> hasKey(String key);
}

