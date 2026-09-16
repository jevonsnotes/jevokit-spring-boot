package springbootkit.redis.cache.generator.extend;

import cn.hutool.json.JSONUtil;
import springbootkit.redis.cache.generator.AbstractKeyGenerator;

import java.lang.reflect.Method;

/**
 * 全局缓存key生成策略，防止同名方法读取缓存一致的问题
 *
 */
public class GlobalKeyGenerator extends AbstractKeyGenerator {
    public static final String NAME = "globalKeyGenerator";
    public static final String PREFIX = "GLOBAL:";
	public static final String PREFIX_PATTERN = PREFIX;
	public static final String EVICT = "*" + PREFIX + "*";

    @Override
    public String generate(Object target, Method method, Object... params) {
        StringBuilder builder = new StringBuilder();
        builder
                .append(getFormatPrefix())
                .append(method.getDeclaringClass().getSimpleName())
                .append(".")
                .append(method.getName())
                .append(":")
                .append(JSONUtil.toJsonStr(params));
        return builder.toString();
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public String getFormatPrefix() {
        return PREFIX_PATTERN;
    }


}
