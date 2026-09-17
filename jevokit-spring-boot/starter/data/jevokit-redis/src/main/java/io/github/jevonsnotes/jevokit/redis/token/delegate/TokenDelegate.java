package io.github.jevonsnotes.jevokit.redis.token.delegate;


import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import io.github.jevonsnotes.jevokit.redis.lock.locker.ReentrantLocker;
import io.github.jevonsnotes.jevokit.redis.token.strategy.TokenStrategy;
import io.github.jevonsnotes.jevokit.redis.util.RedisLock;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class TokenDelegate {
    private static final String TOKEN_LOCK_PREFIX = "TOKEN_LOCK:";
    private final RedisTemplate<Object, Object> redisTemplate;

    /**
     * 获取token
     * 逻辑流程：
     * 1、redis token不为空→返回
     * 2、redis token为空 → 阻塞锁（2s）→ redis token不为空 → 返回
     * 3、redis token为空 → 阻塞锁（2s）→ redis token为空 → 调用策略getToken → 返回
     * 4、超时 → 调用策略timeout()
     *
     * @return
     */
    public String getToken(TokenStrategy strategy) {

        ValueOperations<Object, Object> ops = redisTemplate.opsForValue();
        String token = (String) ops.get(strategy.getTokenKey());
        if (StrUtil.isNotBlank(token)) {
            return token;
        } else {
            return new ReentrantLocker<String>() {
                @Override
                public String wrap() {
                    String token = (String) ops.get(strategy.getTokenKey());
                    if (StrUtil.isNotBlank(token)) {
                        return token;
                    } else {
                        return strategy.getToken(strategy.getTokenKey());
                    }
                }

                @Override
                public boolean lock(String key) {
                    return RedisLock.tryLock(key, 2, 60, TimeUnit.SECONDS);
                }

                /**
                 * 获取超时处理
                 * @return
                 */
                @Override
                public String lockFailed() {
                    return strategy.timeOut(strategy.getTokenKey());
                }
            }.exec(TOKEN_LOCK_PREFIX + strategy.getTokenKey());

        }
    }

    /**
     * 刷新token
     * 逻辑流程：
     * 1、阻塞锁（2s）→ 调用策略refreshToken → 返回
     * 2、超时 → 调用策略timeout()
     *
     * @return
     */
    public String refreshToken(TokenStrategy strategy) {
        return new ReentrantLocker<String>() {
            @Override
            public String wrap() {
                return strategy.refreshToken(strategy.getTokenKey());
            }

            @Override
            public boolean lock(String key) {
                return RedisLock.tryLock(key, 2, 60, TimeUnit.SECONDS);
            }

            /**
             * 刷新超时处理
             */
            @Override
            public String lockFailed() {
                return strategy.timeOut(strategy.getTokenKey());
            }
        }.exec(TOKEN_LOCK_PREFIX + strategy.getTokenKey());
    }


}
