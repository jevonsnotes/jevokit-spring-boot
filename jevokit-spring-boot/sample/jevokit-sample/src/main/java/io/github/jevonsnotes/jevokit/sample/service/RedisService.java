package io.github.jevonsnotes.jevokit.sample.service;

import cn.hutool.core.lang.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import io.github.jevonsnotes.jevokit.redis.cache.consts.RConst;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.GlobalKeyGenerator;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.UserKeyGenerator;

/**
 * @Description TODO
 * @Date 2022/7/25 11:16
 * @Version 0.0.1
 */
@Service
public class RedisService {
    /**
     * 用户缓存测试（随机失效时间 5-10分钟）
     * @return
     */
    @Cacheable(value = "testCache",keyGenerator = UserKeyGenerator.NAME,unless = RConst.Unless.REMOTE_IF_NULL)
    public String userCache(){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 用户缓存测试（指定失效时间）
     * @return
     */
    @Cacheable(value = "testCache#expire=1800",keyGenerator = UserKeyGenerator.NAME)
    public String userCacheWithExpire(){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 全局缓存测试
     * @return
     */
    @Cacheable(value = "testCache",keyGenerator = GlobalKeyGenerator.NAME)
    public String globalCache(){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 清除全局缓存
     */
    @CacheEvict(cacheNames = GlobalKeyGenerator.EVICT,allEntries = true)
    public void globalCacheEvict() {
    }
    /**
     * 清除用户缓存
     */
    @CacheEvict(cacheNames = UserKeyGenerator.EVICT,allEntries = true)
    public void userCacheEvict() {
    }
}
