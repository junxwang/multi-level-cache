package io.github.wangjx.multilevelcache.model;

/**
 * Redis广播的缓存失效消息
 * @author wangjx
 */
public class CacheInvalidationMessage {

    public enum Action {
        EVICT,
        CLEAR
    }

    private String instanceId;
    private String cacheName;
    private Action action;
    private String key;

    public CacheInvalidationMessage() {
    }

    public CacheInvalidationMessage(String instanceId, String cacheName, Action action, String key) {
        this.instanceId = instanceId;
        this.cacheName = cacheName;
        this.action = action;
        this.key = key;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}


