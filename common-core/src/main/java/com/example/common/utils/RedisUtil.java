package com.example.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 通用 Redis 工具类。
 * 封装常用 Redis 操作，统一使用 RedisTemplate<String, Object>。
 * 序列化方案由 {@link com.example.common.config.RedisConfig} 配置，
 * Key 为 String，Value 为 JSON（带类型信息）。
 *
 * 高可用设计：Redis 仅作为热点缓存，任何 Redis 异常都会被捕获并记录日志，
 * 不会向上抛出中断主流程（缓存降级），调用方应回退到 MySQL 查询。
 */
@Component
public class RedisUtil {

    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

    /** 空值标记：表示该key在DB中不存在，用于缓存穿透保护 */
    private static final String NULL_VALUE = "NULL";

    /** 互斥锁Key前缀：用于缓存击穿保护 */
    private static final String LOCK_KEY_PREFIX = "lock:";

    /** 空值缓存过期时间：5分钟，短于正常缓存，避免长期占用 */
    private static final long NULL_EXPIRE_MINUTES = 5;

    /** 互斥锁过期时间：10秒，防止持锁方宕机导致锁无法释放 */
    private static final long LOCK_EXPIRE_SECONDS = 10;

    /** 未获锁等待时间：50毫秒，等待持锁请求完成缓存重建 */
    private static final long LOCK_WAIT_MILLIS = 50;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写入缓存（无过期时间，永久存储）。
     * 写入失败仅记录日志，不影响主流程。
     */
    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.error("Redis set 失败, key={}", key, e);
            return false;
        }
    }

    /**
     * 写入缓存并设置过期时间。
     * 写入失败仅记录日志，不影响主流程。
     *
     * @param timeout 过期时间数值
     * @param unit    时间单位
     */
    public boolean set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            return true;
        } catch (Exception e) {
            log.error("Redis set 失败, key={}, timeout={}", key, timeout, e);
            return false;
        }
    }

    /**
     * 写入缓存并设置过期时间（便捷方法，单位：秒）
     */
    public boolean set(String key, Object value, long seconds) {
        return set(key, value, seconds, TimeUnit.SECONDS);
    }

    /**
     * 读取缓存。
     * Redis 异常时降级返回 null，调用方应回退到 MySQL 查询。
     *
     * @return 缓存值，不存在或 Redis 异常时返回 null
     */
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis get 失败(降级回源MySQL), key={}", key, e);
            return null;
        }
    }

    /**
     * 删除单个缓存 key。
     * 删除失败仅记录日志，不影响主流程。
     */
    public Boolean delete(String key) {
        try {
            return redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis delete 失败, key={}", key, e);
            return false;
        }
    }

    /**
     * 批量删除缓存 key。
     * 删除失败仅记录日志，不影响主流程。
     *
     * @return 成功删除的数量
     */
    public Long delete(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return 0L;
        }
        try {
            return redisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("Redis delete 失败, keys={}", keys, e);
            return 0L;
        }
    }

    /**
     * 判断 key 是否存在。
     * Redis 异常时降级返回 false。
     */
    public Boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("Redis hasKey 失败, key={}", key, e);
            return false;
        }
    }

    /**
     * 尝试获取互斥锁（SETNX + 过期时间），用于缓存击穿保护。
     * Redis 异常时返回 false，调用方走无锁降级逻辑。
     */
    public boolean tryLock(String key, long seconds) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(key, "1", seconds, TimeUnit.SECONDS));
        } catch (Exception e) {
            log.error("Redis tryLock 失败, key={}", key, e);
            return false;
        }
    }

    /**
     * 释放互斥锁。
     * 注意：简易版不校验锁持有者，生产环境应升级为 UUID + Lua 脚本防止误删他人锁。
     */
    public void unlock(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis unlock 失败, key={}", key, e);
        }
    }

    /**
     * 缓存读取模板方法，封装穿透/击穿/雪崩三大防护：
     * - 穿透防护：DB不存在时缓存空值标记（短TTL），命中标记直接返回 null 不再查DB
     * - 击穿防护：缓存未命中时互斥锁 + 双重检查，同一时刻只放行一个请求回源重建
     * - 雪崩防护：真值写入使用「基础TTL + 随机抖动」，打散过期时间点
     *
     * @param key         缓存key
     * @param baseTimeout 基础过期时间
     * @param jitterBound 随机抖动上限（实际TTL = baseTimeout + 0~jitterBound）
     * @param unit        时间单位
     * @param loader      回源数据源（如MySQL查询），返回 null 表示数据不存在
     * @return 缓存值或回源值；数据不存在返回 null，由调用方决定抛异常等业务语义
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, long baseTimeout, long jitterBound, TimeUnit unit, Supplier<T> loader) {
        // ① 先查缓存（命中真值直接返回；命中空值标记视为不存在）
        Object cached = get(key);
        if (cached != null) {
            return isNullValue(cached) ? null : (T) cached;
        }

        // ② 尝试获取互斥锁，同一时刻只放行一个请求回源重建缓存
        String lockKey = LOCK_KEY_PREFIX + key;
        if (tryLock(lockKey, LOCK_EXPIRE_SECONDS)) {
            try {
                // 双重检查：等锁期间缓存可能已被其他请求重建
                cached = get(key);
                if (cached != null) {
                    return isNullValue(cached) ? null : (T) cached;
                }
                return loadAndCache(key, baseTimeout, jitterBound, unit, loader);
            } finally {
                unlock(lockKey);
            }
        }

        // ③ 未获锁：其他请求正在重建，短暂等待后重读缓存
        sleepQuietly(LOCK_WAIT_MILLIS);
        cached = get(key);
        if (cached != null) {
            return isNullValue(cached) ? null : (T) cached;
        }

        // ④ 仍未命中（含Redis宕机无法加锁的降级场景），回退回源并重建缓存
        return loadAndCache(key, baseTimeout, jitterBound, unit, loader);
    }

    /**
     * 写入空值标记（缓存穿透防护）。
     */
    public boolean setNullValue(String key, long timeout, TimeUnit unit) {
        return set(key, NULL_VALUE, timeout, unit);
    }

    /**
     * 判断缓存值是否为空值标记。
     */
    public boolean isNullValue(Object cached) {
        return NULL_VALUE.equals(cached);
    }

    /**
     * 写入缓存并附加随机抖动TTL（缓存雪崩防护）：实际TTL = baseTimeout + 0~jitterBound。
     */
    public boolean setWithJitter(String key, Object value, long baseTimeout, long jitterBound, TimeUnit unit) {
        long ttl = baseTimeout + ThreadLocalRandom.current().nextLong(jitterBound + 1);
        return set(key, value, ttl, unit);
    }

    /**
     * 回源并重建缓存：loader 返回 null 时缓存空值标记（短TTL），否则缓存真值（抖动TTL）。
     */
    private <T> T loadAndCache(String key, long baseTimeout, long jitterBound, TimeUnit unit, Supplier<T> loader) {
        T value = loader.get();
        if (value == null) {
            setNullValue(key, NULL_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } else {
            setWithJitter(key, value, baseTimeout, jitterBound, unit);
        }
        return value;
    }

    /**
     * 短暂等待，用于未获锁场景等待缓存重建完成。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
