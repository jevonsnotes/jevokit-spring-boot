package io.github.jevonsnotes.jevokit.redis.token.strategy;

import cn.hutool.core.lang.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 默认获取token案例
 */
@RequiredArgsConstructor
public class DefaultTokenStrategy implements TokenStrategy{

    private final RedisTemplate<Object, Object> redisTemplate;

    @Override
/**
    * 取 token：实现上等于 refreshToken——每次调用都换新值并覆盖旧 token（旧值立即失效）。
    * @param key token 存储键
    * @return 新生成的 token（32 位无横线 UUID）
    */
    public String getToken(String key) {
        return refreshToken(key);
    }

    @Override
/**
    * 刷新 token：生成新 UUID 以 1 小时 TTL 写入并覆盖旧值。
    * @param key token 存储键
    * @return 新 token
    */
    public String refreshToken(String key) {
        String token = UUID.fastUUID().toString(true);
        redisTemplate.opsForValue().set(key,token,60*60, TimeUnit.SECONDS);
        return token;
    }

    @Override
/**
    * 缺省 token 键名。
    * @return 固定值 defaultTokenKey
    */
    public String getTokenKey() {
        return "defaultTokenKey";
    }
}
