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

    @Override
    public boolean supports(MethodParameter methodParameter, Class aClass) {
        return ConfigHolder.getXssMeta().isEnabled() && !ConfigHolder.getXssMeta().isIgnoreResponse();
    }

    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        if (!ObjectUtils.isEmpty(o) && !methodParameter.getMethod().getName().equals("error")) {
            o = SecurityUtil.cleanObjForStr(o, content -> xssCleaner.clean(content));
        }
        return o;
    }
}
