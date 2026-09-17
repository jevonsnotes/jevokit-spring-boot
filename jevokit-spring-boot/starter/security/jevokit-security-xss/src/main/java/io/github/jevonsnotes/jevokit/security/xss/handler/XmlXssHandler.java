package io.github.jevonsnotes.jevokit.security.xss.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Xml报文Xss处理器
 */
@ConditionalOnBean(XssCleaner.class)
@ControllerAdvice
@RequiredArgsConstructor
public class XmlXssHandler implements RequestBodyAdvice {
    private final XssCleaner xssCleaner;


    /**
     * 仅对 JAXB（XML 报文）转换且当前请求防护开启时启用本处理器。
     *
     * @return true 表示 XML 请求体走本处理器的清洗逻辑
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return Jaxb2RootElementHttpMessageConverter.class.isAssignableFrom(converterType) && ConfigHolder.getXssMeta().isEnabled();
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
     * XML body 反序列化为对象后，递归遍历对象的全部 String 字段/元素执行清洗。
     *
     * @return 清洗后的对象；body 为空时原样返回
     */
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!ObjectUtils.isEmpty(body)) {
            body = SecurityUtil.cleanObjForStr(body, content -> xssCleaner.clean(content));
        }
        return body;
    }

    /** 空 body 直通（无内容可清洗）。 */
    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
