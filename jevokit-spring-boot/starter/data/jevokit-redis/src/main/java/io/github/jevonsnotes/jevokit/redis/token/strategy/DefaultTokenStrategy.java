package io.github.jevonsnotes.jevokit.redis.token.strategy;

import cn.hutool.core.lang.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @Description 默认获取token案例
 * @Date 2022/7/27 14:01
 * @Version 0.0.1
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
