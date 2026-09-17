package io.github.jevonsnotes.jevokit.security.sql.inject.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;
import io.github.jevonsnotes.jevokit.security.sql.inject.util.SqlInjectUtil;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Xml报文处理器
 */
@ControllerAdvice
@RequiredArgsConstructor
public class RequestBodySqlInjectHandler implements RequestBodyAdvice {

    private final SqlInjectProperties properties;

    /**
     * 仅当前请求 SQL 注入防护开启（ThreadLocal 由拦截器写入）时启用本处理器。
     *
     * @return true 表示请求体走本处理器的清洗逻辑
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ConfigHolder.getSIMeta().isEnabled();
    }

    /**
     * Invoked second before the request body is read and converted.
     *
     * @param inputMessage  the request
     * @param parameter     the target method parameter
     * @param targetType    the target type, not necessarily the same as the method
     *                      parameter type, e.g. for {@code HttpEntity<String>}.
     * @param converterType the converter used to deserialize the body
     * @return the input request or a new instance, never {@code null}
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        return inputMessage;
    }


    /**
     * 请求体反序列化为对象后，递归遍历对象的全部 String 字段/元素执行 SQL 注入清洗
     * （规则为默认污染表 + 附加表合并，语义见 {@link SqlInjectUtil#clean(String, List)}）。
     *
     * @return 清洗后的对象；body 为空时原样返回
     */
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!ObjectUtils.isEmpty(body)) {
            body = SecurityUtil.cleanObjForStr(body, content -> SqlInjectUtil.clean(content,properties.getAllTaintedPatterns()));
        }
        return body;
    }

    /** 空 body 直通（无内容可清洗）。 */
    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

}
