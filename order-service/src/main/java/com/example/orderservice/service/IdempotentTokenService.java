package com.example.orderservice.service;

import com.example.common.exception.DuplicateSubmitException;
import com.example.orderservice.config.OrderBusinessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 下单接口幂等 token 服务（"先领号、后提交"机制）。
 *
 * <p>整体流程：
 * <ol>
 *   <li>前端进入下单页时调用 GET /orders/idempotent-token 领取一次性令牌；</li>
 *   <li>提交下单时把令牌放在 X-Idempotent-Token 请求头里；</li>
 *   <li>服务端用 Lua 脚本原子地"存在即删除"该令牌：
 *       删除成功 = 首次提交，放行下单；删除失败（键不存在）= 重复提交 / 令牌过期，
 *       抛 {@link DuplicateSubmitException} → 全局异常处理器返回 409。</li>
 * </ol></p>
 *
 * <p>为什么消费必须用 Lua 而不是"GET + DEL"两步：两步之间存在时间窗口，
 * 两个并发请求可能都 GET 到令牌、又都执行 DEL，双双放行 → 幂等失效。
 * Lua 脚本在 Redis 单线程内整体执行，天然原子。
 * 另：本环境 Redis 为 5.0.14，没有 6.2+ 才有的 GETDEL 命令，Lua 是唯一原子做法。</p>
 */
@Service
public class IdempotentTokenService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentTokenService.class);

    /** Redis key 前缀，完整 key 形如 order:idempotent:token:9f8b...-c2 */
    private static final String TOKEN_KEY_PREFIX = "order:idempotent:token:";

    /**
     * 原子"存在即删"脚本：KEYS[1] 存在则 DEL 并返回 1，否则返回 0。
     * 脚本内容固定、无参数，作为静态常量只解析一次（DefaultRedisScript 会缓存 SHA1，
     * 实际走 EVALSHA，避免每次全量传输脚本）。
     */
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) then "
                    + "return redis.call('DEL', KEYS[1]) "
                    + "else "
                    + "return 0 "
                    + "end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    /** 令牌有效期来自 Nacos 配置中心（order.business.idempotent-token-ttl-seconds），动态可调 */
    private final OrderBusinessProperties orderBusinessProperties;

    public IdempotentTokenService(StringRedisTemplate redisTemplate,
                                  OrderBusinessProperties orderBusinessProperties) {
        this.redisTemplate = redisTemplate;
        this.orderBusinessProperties = orderBusinessProperties;
    }

    /**
     * 下发一个一次性幂等令牌：UUID 存入 Redis 并设置 TTL，值固定为 "1"（仅作存在性标记）。
     *
     * @return 令牌本身（UUID 字符串），客户端原样放进 X-Idempotent-Token 请求头
     */
    public String issueToken() {
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(orderBusinessProperties.getIdempotentTokenTtlSeconds());
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, "1", ttl);
        log.info("Issued idempotent token: {} (ttl={}s)", token, ttl.getSeconds());
        return token;
    }

    /**
     * 原子消费令牌：删除成功即首次提交放行；键不存在（已消费 / 已过期 / 伪造）则拒绝。
     *
     * @param token 客户端携带的 X-Idempotent-Token
     * @throws DuplicateSubmitException 令牌不存在或已被消费（重复提交）
     */
    public void consumeToken(String token) {
        List<String> keys = Collections.singletonList(TOKEN_KEY_PREFIX + token);
        Long result = redisTemplate.execute(CONSUME_SCRIPT, keys);
        if (result == null || result == 0L) {
            log.warn("Idempotent token rejected: {} (already consumed / expired / forged)", token);
            throw new DuplicateSubmitException("请勿重复提交（令牌已使用或已过期，请重新获取）");
        }
        log.info("Idempotent token consumed: {}", token);
    }
}
