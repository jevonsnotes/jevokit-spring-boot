package springbootkit.security.xss.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.core.util.SecurityUtil;
import springbootkit.security.xss.cleaner.XssCleaner;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @Description Xml报文Xss处理器
 *
 * @Date 2022/5/24 10:50
 * @Version 0.0.1
 */
@ConditionalOnBean(XssCleaner.class)
@ControllerAdvice
@RequiredArgsConstructor
public class XmlXssHandler implements RequestBodyAdvice {
    private final XssCleaner xssCleaner;


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


    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!ObjectUtils.isEmpty(body)) {
            body = SecurityUtil.cleanObjForStr(body, content -> xssCleaner.clean(content));
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
