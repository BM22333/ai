package me.beiming.aichat.tool;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的会话级缓存工具。
 * 用于缓存 ChatClient、会话状态等对象，30 分钟无访问自动过期，最大 1000 条。
 */
@Slf4j
public class SessionTool {

    /** 默认缓存过期时间（分钟） */
    private static final long DEFAULT_TTL_MINUTES = 30;

    private static final Cache<String, Object> SESSION_CACHE = Caffeine.newBuilder()
            .expireAfterAccess(DEFAULT_TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(1000)
            .recordStats()
            .build();


    public static Object get(String cacheKey) {
        return SESSION_CACHE.getIfPresent(cacheKey);
    }

    public static void set(String cacheKey, Object object) {
        SESSION_CACHE.put(cacheKey, object);
    }

    public static void remove(String cacheKey) {
        SESSION_CACHE.invalidate(cacheKey);
    }

    public static boolean contains(String cacheKey) {
        return SESSION_CACHE.getIfPresent(cacheKey) != null;
    }

    public static int size() {
        return (int) SESSION_CACHE.estimatedSize();
    }

    public static void clear() {
        SESSION_CACHE.invalidateAll();
    }

    public static CacheStats getStats() {
        return SESSION_CACHE.stats();
    }
}
