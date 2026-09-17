package io.github.jevonsnotes.jevokit.security.xss.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.configuration.annotation.XssClean;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssMeta;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
public class XssCleanInterceptor implements AsyncHandlerInterceptor {
    private final XssProperties xssProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非控制层跳过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 开关校验
        if (!xssProperties.isEnabled()) {
            return true;
        }
        // Xss忽略（方法级覆盖类级，Spring 惯例；方法无注解时回退类级）
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        XssClean xssClean = handlerMethod.getMethodAnnotation(XssClean.class);
        if (xssClean == null) {
            xssClean = handlerMethod.getMethod().getDeclaringClass().getAnnotation(XssClean.class);
        }

        if (xssClean == null || !xssClean.disabled()) {
            XssMeta meta = ConfigHolder.getXssMeta();
            meta.setMode(xssProperties.getMode());
            meta.setEnabled(Boolean.TRUE);
            meta.setIgnoreResponse(xssProperties.isIgnoreResponse());

            if (xssClean != null){
                if(xssClean.mode().length>0) {
                    ConfigHolder.getXssMeta().setMode(xssClean.mode()[0]);
                }
                ConfigHolder.getXssMeta().setIgnoreResponse(xssClean.ignoreResponse());
            }

        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ConfigHolder.removeXss();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ConfigHolder.removeXss();
    }
}
