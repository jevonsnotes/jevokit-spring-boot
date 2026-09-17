package io.github.jevonsnotes.jevokit.nogen.interceptor;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import io.github.jevonsnotes.jevokit.nogen.config.JevokitNoGenProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
@Slf4j
public class NoGenInterceptor implements AsyncHandlerInterceptor {
    private final JevokitNoGenProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非控制层跳过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // token校验
        if (!properties.isEnableApi() || !StrUtil.trimToEmpty(properties.getAccessToken()).equals(StrUtil.trimToEmpty(request.getHeader("accessToken-nogen")))) {
            log.error("未授权访问[{}]，请检查是否开启API并配置访问密钥[{}]",request.getRequestURI(),"accessToken-nogen");
            response.sendError(HttpStatus.FORBIDDEN.value());
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    }
}
