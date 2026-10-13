package io.github.jevonsnotes.jevokit.redis.cache.generator.extend;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import io.github.jevonsnotes.jevokit.redis.cache.generator.AbstractKeyGenerator;

import javax.servlet.http.HttpServletRequest;

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
    /**
    * 用户维度键：前缀含用户标识 + 方法全名 + 入参 JSON——同方法不同用户天然分键。
    * @param target 目标对象
    * @param method 目标方法
    * @param params 入参
    * @return 用户维度缓存键
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
    * 用户键前缀。
    * @return USER:
    */
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    /**
    * 用户键格式化前缀：取请求头 userCode（无请求上下文回落 00000000 占位）。
    * @return 形如 USER:用户码: 的前缀
    */
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
