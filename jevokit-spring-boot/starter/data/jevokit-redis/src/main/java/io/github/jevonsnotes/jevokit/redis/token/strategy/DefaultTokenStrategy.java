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
    public String getToken(String key) {
        return refreshToken(key);
    }

    @Override
    public String refreshToken(String key) {
        String token = UUID.fastUUID().toString(true);
        redisTemplate.opsForValue().set(key,token,60*60, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public String getTokenKey() {
        return "defaultTokenKey";
    }
}
