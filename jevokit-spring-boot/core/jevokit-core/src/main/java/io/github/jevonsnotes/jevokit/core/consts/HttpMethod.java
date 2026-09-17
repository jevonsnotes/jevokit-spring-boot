package io.github.jevonsnotes.jevokit.core.consts;

import java.util.HashMap;
import java.util.Map;

import org.springframework.lang.Nullable;


/**
 * HTTP 方法枚举（Spring 5 之前 org.springframework.http.HttpMethod 的类值形态等价物）：
 * 大小写敏感，{@link #resolve(String)} 按名解析、未知返回 null，{@link #matches(String)} 判等。
 * 供需要将方法当值传递/比较的过滤器与工具使用。
 */
public enum HttpMethod {

    GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE;


    private static final Map<String, HttpMethod> mappings = new HashMap<>(16);

    static {
        for (HttpMethod httpMethod : values()) {
            mappings.put(httpMethod.name(), httpMethod);
        }
    }


    /**
     * 按名称解析 HTTP 方法
     *
     * @param method 方法名（大小写敏感），null 安全
     * @return 对应枚举值；名称未知或入参为 null 时返回 null
     */
    @Nullable
    public static HttpMethod resolve(@Nullable String method) {
        return (method != null ? mappings.get(method) : null);
    }

    /**
     * 判断给定方法名是否与本枚举值相同
     *
     * @param method 方法名（大小写敏感）
     * @return 相同为 true
     */
    public boolean matches(String method) {
        return name().equals(method);
    }

}
