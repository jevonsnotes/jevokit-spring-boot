package io.github.jevonsnotes.jevokit.sample.service;

import cn.hutool.core.lang.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import io.github.jevonsnotes.jevokit.redis.cache.consts.RConst;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.GlobalKeyGenerator;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.UserKeyGenerator;

 /**
 * redis 缓存演示服务：@Cacheable 三形态——随机 TTL（UserKeyGenerator + unless 空
 * 值不缓存）、固定 TTL（value 名后缀 #expire=秒）、全局/用户两套键空间与逐出。
 */
@Service
public class RedisService {
    /**
     * 用户缓存测试（随机失效时间 5-10分钟）
     * @return
     */
    @Cacheable(value = "testCache",keyGenerator = UserKeyGenerator.NAME,unless = RConst.Unless.REMOTE_IF_NULL)
    /**
    * 用户缓存演示：随机 TTL 5-10 分钟防雪崩，unless 空值不缓存。
       * @return 缓存值（未命中时生成新值）
    */
    public String userCache(){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 用户缓存测试（指定失效时间）
     * @return
     */
    @Cacheable(value = "testCache#expire=1800",keyGenerator = UserKeyGenerator.NAME)
    /**
    * 指定 TTL 缓存演示：缓存名 #expire=1800 后缀语法。
       * @return 缓存值
    */
    public String userCacheWithExpire(){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 全局缓存测试
     * @return
     */
    @Cacheable(value = "testCache",keyGenerator = GlobalKeyGenerator.NAME)
    /**
    * 全局键空间缓存演示（GlobalKeyGenerator，无用户维度）。
       * @return 缓存值
    */
    public String globalCache(){
        return UUID.fastUUID().toString(true);
    }

    /**
     * 清除全局缓存
     */
    @CacheEvict(cacheNames = GlobalKeyGenerator.EVICT,allEntries = true)
    /**
    * 全局缓存全量逐出。
    *
    */
    public void globalCacheEvict() {
    }
    /**
     * 清除用户缓存
     */
    @CacheEvict(cacheNames = UserKeyGenerator.EVICT,allEntries = true)
    /**
    * 用户缓存全量逐出。
    *
    */
    public void userCacheEvict() {
    }
}
