package io.github.jevonsnotes.jevokit.redis.cache.generator.extend.rocketmq;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import io.github.jevonsnotes.jevokit.redis.cache.generator.AbstractKeyGenerator;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;


/**
 * 基于用户代码的缓存key生成策略，防止同名方法读取缓存一致的问题
 *
 */
public class RocketMqV5KeyGenerator extends AbstractKeyGenerator {
	public static final String NAME = "rocketMqKeyGenerator";
	public static final String PREFIX = "ROCKET_MQ:";
	public static final String PREFIX_PATTERN = PREFIX;
	public static final String EVICT = "*" + PREFIX + "*";


	@Override
	public String generate(Object target, Method method, Object... params) {
		Assert.isTrue(ArrayUtil.isNotEmpty(params),"入参不能为空!");
		Object value = ReflectUtil.getFieldValue(params[0], "messageId");
		Assert.notNull(value,"messageId 不能为空！");

		
		StringBuilder builder = new StringBuilder();
		builder
		.append(getFormatPrefix())
		.append(method.getDeclaringClass().getSimpleName())
		.append(".")
		.append(method.getName())
		.append(":")
		.append(value.toString());
		return builder.toString();
	}

	@Override
	public String getPrefix() {
		return PREFIX;
	}

	public String getFormatPrefix() {
		return PREFIX_PATTERN;
	}
	

}
