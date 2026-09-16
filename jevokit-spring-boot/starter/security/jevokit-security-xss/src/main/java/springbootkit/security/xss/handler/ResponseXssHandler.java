package springbootkit.security.xss.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.core.util.SecurityUtil;
import springbootkit.security.xss.cleaner.XssCleaner;

/**
 * @Description 返回体处理器
 * @Date 2022/5/23 15:33
 * @Version 0.0.1
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
