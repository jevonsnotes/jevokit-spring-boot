package io.github.jevonsnotes.jevokit.redis.cache.generator.extend;

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
public class UserKeyGenerator extends AbstractKeyGenerator {
    public static final String NAME = "userKeyGenerator";
    public static final String PREFIX = "USER:";
    public static final String PREFIX_PATTERN = PREFIX + "%s:";
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
        String userCode = "00000000";
        RequestAttributes req = RequestContextHolder.getRequestAttributes();
        if (req != null) {
            HttpServletRequest request = ((ServletRequestAttributes) req).getRequest();
            userCode = StrUtil.emptyToDefault(request.getHeader("userCode"), userCode);//do something to get userCode
        }
        return String.format(PREFIX_PATTERN, userCode);
    }


}
