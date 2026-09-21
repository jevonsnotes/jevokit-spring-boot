package io.github.jevonsnotes.jevokit.redis.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.stereotype.Component;


public class GlobalCacheKeyPrefix implements CacheKeyPrefix{

	@Value("${jevokit.redis.cache.prefix:${spring.application.name:unknown}}:%s:")
	public String CACHE_NAMESPACE_PATTERN;
	@Override
 /**
 * 缓存键前缀：以 jevokit.redis.cache.prefix（缺省 spring.application.name）+ 缓存名拼前缀，
 * 实现多系统共库 Redis 的键空间隔离。
 * @param cacheName 缓存名
 * @return 形如 app:cacheName: 的前缀
 */
	public String compute(String cacheName) {
		return String.format(CACHE_NAMESPACE_PATTERN, cacheName);
	}
}
