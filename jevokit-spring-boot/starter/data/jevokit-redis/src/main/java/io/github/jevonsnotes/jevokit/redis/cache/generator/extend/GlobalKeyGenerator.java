package io.github.jevonsnotes.jevokit.redis.cache.generator.extend;

import cn.hutool.json.JSONUtil;
import io.github.jevonsnotes.jevokit.redis.cache.generator.AbstractKeyGenerator;

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
    /**
    * 全局键：无身份段，方法全名 + 入参 JSON。
    * @param target 目标对象
    * @param method 目标方法
    * @param params 入参
    * @return 拼装的缓存键
    */
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
    /**
    * 全局键前缀。
    * @return GLOBAL:
    */
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    /**
    * 全局键格式化前缀（无身份维度）。
    * @return 前缀串
    */
    public String getFormatPrefix() {
        return PREFIX_PATTERN;
    }


}
