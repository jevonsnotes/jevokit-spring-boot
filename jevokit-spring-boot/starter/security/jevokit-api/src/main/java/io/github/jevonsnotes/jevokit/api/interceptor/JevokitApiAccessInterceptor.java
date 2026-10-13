package io.github.jevonsnotes.jevokit.api.interceptor;

import cn.hutool.core.text.AntPathMatcher;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiAccessMapperProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.jevonsnotes.jevokit.api.context.JSecureContext;

@RequiredArgsConstructor
@Slf4j
public class JevokitApiAccessInterceptor implements AsyncHandlerInterceptor {
    private AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final JevokitApiProperties properties;

    /**
     * 简单 token 准入：路径命中 access.tokens 配置时校验请求头令牌，不匹配返回 403。
     *
     * @return true 放行；false 已写出 403 拒绝（未配置 tokens 的路径恒放行）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非控制层跳过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // token校验

        for(JevokitApiAccessMapperProperties mapper : properties.getAccess().getTokens()){
            if(antPathMatcher.match(mapper.getPath(), request.getServletPath())){
                String header = request.getHeader(properties.getAccess().getHeader());
                if( StrUtil.isBlank(header) || !header.equals(mapper.getToken())) {
                    log.info("{} 无访问权限,请配置访问密钥，并携带请求头{}访问  ！", request.getServletPath(),properties.getAccess().getHeader());
                    response.sendError(HttpStatus.FORBIDDEN.value());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 请求结束/异常时清理协议上下文（必经且最后执行的生命周期点），防线程池复用串请求。
     *
     * @param ex handler 抛出的异常（无异常为 null）；无论有无异常都需清理
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        JSecureContext.remove();
    }

    /**
     * 异步 dispatch 起始时清理原线程上的协议上下文（原线程随即归还线程池）；
     * 异步完成后的重新分发由该次请求的 afterCompletion 覆盖。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        JSecureContext.remove();
    }
}
