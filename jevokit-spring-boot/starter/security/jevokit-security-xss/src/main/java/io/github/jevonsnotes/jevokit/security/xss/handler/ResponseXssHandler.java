package io.github.jevonsnotes.jevokit.security.xss.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;

/**
 * 返回体处理器
 */
@ConditionalOnBean(XssCleaner.class)
@ControllerAdvice
@RequiredArgsConstructor
public class ResponseXssHandler implements ResponseBodyAdvice {

    private final XssCleaner xssCleaner;

    /**
     * 当前请求防护开启且未忽略响应（spkit.security.xss.ignore-response=false）时启用响应清洗。
     *
     * @return true 表示响应体走本处理器的清洗逻辑
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Class aClass) {
        return ConfigHolder.getXssMeta().isEnabled() && !ConfigHolder.getXssMeta().isIgnoreResponse();
    }

    /**
     * 序列化前递归清洗响应对象中的全部 String 值（防止存储型 XSS 经响应回流）。
     *
     * @return 清洗后的对象；空对象与 Spring 错误处理方法（error）直通不清洗
     */
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        if (!ObjectUtils.isEmpty(o) && !methodParameter.getMethod().getName().equals("error")) {
            o = SecurityUtil.cleanObjForStr(o, content -> xssCleaner.clean(content));
        }
        return o;
    }
}
