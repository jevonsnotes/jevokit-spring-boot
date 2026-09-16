package springbootkit.security.pt.handler;

import cn.hutool.core.collection.CollectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import springbootkit.configuration.security.pathtraversal.PathTraversalProperties;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.core.util.SecurityUtil;
import springbootkit.security.pt.util.PathTraversalUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @Description Xml报文处理器
 *
 * @Date 2022/5/24 10:50
 * @Version 0.0.1
 */
@ControllerAdvice
@RequiredArgsConstructor
public class RequestBodyPathTraversalHandler implements RequestBodyAdvice {
    public static final List<String> PATTERNS = CollectionUtil.toList("\\.\\./","/etc/.*");

    private final PathTraversalProperties properties;

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ConfigHolder.getPtMeta().isEnabled();
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


    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!ObjectUtils.isEmpty(body)) {
            body = SecurityUtil.cleanObjForStr(body, content -> PathTraversalUtil.clean(content,properties.getAllTaintedPatterns()));
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }



}
