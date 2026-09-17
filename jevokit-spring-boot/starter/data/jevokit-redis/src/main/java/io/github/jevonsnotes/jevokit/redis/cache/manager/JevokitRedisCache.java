package io.github.jevonsnotes.jevokit.redis.cache.manager;

import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.lang.Nullable;
import io.github.jevonsnotes.jevokit.core.util.SecureRandomUtil;
import io.github.jevonsnotes.jevokit.redis.cache.consts.RConst;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.GlobalKeyGenerator;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 自定义RedisCache 重写 put putIfAbsent 逻辑，实现配置ttl或者动态ttl
 */
public class JevokitRedisCache extends RedisCache {
    private static final int MAX_EXPIRE_TIME = 600;//最大失效时间（秒）
    private static final int MIN_EXPIRE_TIME = 300;//最小失效时间（秒）
    /**
     * Create new {@link RedisCache}.
     *
     * @param name        must not be {@literal null}.
     * @param cacheWriter must not be {@literal null}.
     * @param cacheConfig must not be {@literal null}.
     */
    protected JevokitRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig) {
        super(name, cacheWriter, cacheConfig);
    }

    @Override
/**
    * 写缓存：按键名后缀 #expire=秒 提取 TTL（无后缀走配置默认），null 值且未允许时抛异常。
    * @param key 缓存键（支持 TTL 后缀语法）
    * @param value 缓存值（null 受 isAllowNullValues 约束）
    * @throws IllegalArgumentException 不允许 null 值而存入 null
    */
    public void put(Object key, Object value) {

        Object cacheValue = preProcessCacheValue(value);

        if (!isAllowNullValues() && cacheValue == null) {

            throw new IllegalArgumentException(String.format(
                    "Cache '%s' does not allow 'null' values. Avoid storing null via '@Cacheable(unless=\"#result == null\")' or configure RedisCache to allow 'null' via RedisCacheConfiguration.",
                    getName()));
        }
        byte[] andConvertCacheKey = createAndConvertCacheKey(key);
        Duration ttl = getTtl(new String(andConvertCacheKey));

        getNativeCache().put(getName(), andConvertCacheKey, serializeCacheValue(cacheValue), ttl);
    }

    @Override
/**
    * 缺席写入：键已存在时不覆盖，返回现存值。
    * @param key 缓存键
    * @param value 候选值
    * @return 键已存在时返回旧值包装；本次写入成功返回 null（Spring Cache 契约语义）
    */
    public ValueWrapper putIfAbsent(Object key, Object value) {

        Object cacheValue = preProcessCacheValue(value);

        if (!isAllowNullValues() && cacheValue == null) {
            return get(key);
        }
        byte[] andConvertCacheKey = createAndConvertCacheKey(key);
        Duration ttl = getTtl(new String(andConvertCacheKey));

        byte[] result = getNativeCache().putIfAbsent(getName(), andConvertCacheKey, serializeCacheValue(cacheValue),
                ttl);

        if (result == null) {
            return null;
        }

        return new SimpleValueWrapper(fromStoreValue(deserializeCacheValue(result)));
    }

    private Duration getTtl(String keyStr){
        String name = getName();
        Duration ttl = null;
        /*
         * 设置超时时间
         * 使用样例：	@Cacheable(value = "film#expire=60000")
         */
        int index = name.lastIndexOf(RConst.REDIS_EXPIRE_TIME_KEY);
        if (index  > 0){
            //取出对应的时间 1200 index + 1是还有一个=号
            String expireString = name.substring(index  + 1 + RConst.REDIS_EXPIRE_TIME_KEY.length());
            long expireTime = Long.parseLong(expireString);
            ttl =  Duration.ofSeconds(expireTime);
        }else if (shouldExpireWithin(getCacheConfiguration().getTtl())) {
            ttl = getCacheConfiguration().getTtl();
        }
        //非全局缓存则设置随机失效时间
        else if(!keyStr.contains(GlobalKeyGenerator.PREFIX)){
            //随机设置缓存失效时间，避免redis雪崩
            ttl = Duration.ofSeconds(SecureRandomUtil.randomInt(MIN_EXPIRE_TIME,MAX_EXPIRE_TIME));
        }
        return ttl;
    }

    private static boolean shouldExpireWithin(@Nullable Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private byte[] createAndConvertCacheKey(Object key) {
        return serializeCacheKey(createCacheKey(key));
    }
}
