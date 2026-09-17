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

    /**
     * 为命中 URL 的控制层请求写入 XSS 防护 ThreadLocal（依 @XssClean 注解调整模式/忽略响应，方法级覆盖类级）。
     *
     * @return 恒 true（本拦截器只做上下文标记，不拦截请求）
     */
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

    /** 请求结束清理 ThreadLocal（防线程池复用串请求）。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ConfigHolder.removeXss();
    }

    /** 异步请求转为并发处理时清理 ThreadLocal（同 afterCompletion，异步线程另行建立上下文）。 */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ConfigHolder.removeXss();
    }
}
