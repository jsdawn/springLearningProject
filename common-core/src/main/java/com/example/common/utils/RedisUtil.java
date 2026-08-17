package com.example.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

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
}
