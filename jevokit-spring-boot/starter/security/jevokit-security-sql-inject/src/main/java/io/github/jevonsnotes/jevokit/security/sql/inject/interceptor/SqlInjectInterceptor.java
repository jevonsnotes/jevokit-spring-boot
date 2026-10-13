package io.github.jevonsnotes.jevokit.security.sql.inject.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import io.github.jevonsnotes.jevokit.configuration.annotation.SIClean;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectMeta;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
public class SqlInjectInterceptor implements AsyncHandlerInterceptor {
    private final SqlInjectProperties properties;

    /**
     * 为命中 URL 的控制层请求写入 SQL 注入防护 ThreadLocal（依 @SIClean 注解调整模式，方法级覆盖类级）。
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
        if (!properties.isEnabled()) {
            return true;
        }
        // 忽略（方法级覆盖类级，Spring 惯例；方法无注解时回退类级）
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        SIClean siClean = handlerMethod.getMethodAnnotation(SIClean.class);
        if (siClean == null) {
            siClean = handlerMethod.getMethod().getDeclaringClass().getAnnotation(SIClean.class);
        }

        if (siClean == null || !siClean.disabled()) {
            SqlInjectMeta meta = ConfigHolder.getSIMeta();
            meta.setMode(properties.getMode());
            meta.setEnabled(Boolean.TRUE);

            if (siClean != null){
                if(siClean.mode().length>0) {
                    ConfigHolder.getSIMeta().setMode(siClean.mode()[0]);
                }
            }

        }
        return true;
    }

    /** 请求结束清理 ThreadLocal（防线程池复用串请求）。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ConfigHolder.removeSI();
    }

    /** 异步请求转为并发处理时清理 ThreadLocal（同 afterCompletion）。 */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ConfigHolder.removeSI();
    }
}
