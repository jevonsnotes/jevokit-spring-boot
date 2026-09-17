package io.github.jevonsnotes.jevokit.redis.cache.generator;

import org.springframework.cache.interceptor.KeyGenerator;

public abstract class AbstractKeyGenerator implements KeyGenerator{
/**
 * 键空间前缀（如 USER:），子类自定。
 * @return 前缀串
 */
	public abstract String getPrefix();
/**
 * 格式化前缀（含身份段），子类自定。
 * @return 形如 PREFIX:身份: 的前缀
 */
	public abstract String getFormatPrefix();
}
