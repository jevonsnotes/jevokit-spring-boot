package io.github.jevonsnotes.jevokit.nogen.interceptor;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import io.github.jevonsnotes.jevokit.nogen.config.JevokitNoGenProperties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
@Slf4j
public class NoGenInterceptor implements AsyncHandlerInterceptor {
    private final JevokitNoGenProperties properties;

    @Override
    /**
    * accessToken 校验：请求头 accessToken 与配置不符即 401。
    * @param request 请求
    * @param response 响应（401 写回）
    * @param handler 目标处理器
    * @return true=放行；false=拒绝
    * @throws Exception IO 异常
    */
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
    /**
    * 空实现（仅实现契约）。
    * @param request 请求
    * @param response 响应
    * @param handler 处理器
    * @param ex 异常（可空）
    * @throws Exception 契约允许的异常
    */
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }

    @Override
    /**
    * 空实现（仅实现契约）。
    * @param request 请求
    * @param response 响应
    * @param handler 处理器
    * @throws Exception 契约允许的异常
    */
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    }
}
