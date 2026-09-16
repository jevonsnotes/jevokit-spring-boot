package springbootkit.redis.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.stereotype.Component;


public class GlobalCacheKeyPrefix implements CacheKeyPrefix{

	@Value("${spkit.redis.cache.prefix:${spring.application.name:unknown}}:%s:")
	public String CACHE_NAMESPACE_PATTERN;
	@Override
	public String compute(String cacheName) {
		return String.format(CACHE_NAMESPACE_PATTERN, cacheName);
	}
}
