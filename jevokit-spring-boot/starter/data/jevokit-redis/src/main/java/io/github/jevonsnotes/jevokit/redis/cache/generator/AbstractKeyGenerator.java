package io.github.jevonsnotes.jevokit.redis.cache.generator;

import org.springframework.cache.interceptor.KeyGenerator;

public abstract class AbstractKeyGenerator implements KeyGenerator{
	public abstract String getPrefix();
	public abstract String getFormatPrefix();
}
