package io.github.jevonsnotes.jevokit.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.github.jevonsnotes.jevokit.api.interceptor.JevokitApiAccessInterceptor;

/**
 * JSecure API 的 MVC 装配器：向 Spring MVC 注册简单接口 token 准入拦截器
 * {@link JevokitApiAccessInterceptor}（Ant 风格路径 → 访问令牌，spkit.api.access.* 配置）。
 * 使用方式：引入本 starter 自动生效，无路径配置时拦截器直通（未配置 tokens 即不拦截）。
 * <p>关键约束：拦截器以 LOWEST_PRECEDENCE 挂载到 {@code /**}，保证不与业务拦截器争抢执行顺序；
 * 校验失败直接返回 403，不进入 Controller。
 */

@Slf4j
@RequiredArgsConstructor
public class JevokitApiWebMvcConfigurer implements WebMvcConfigurer {
    private final JevokitApiProperties properties;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        JevokitApiAccessInterceptor interceptor = new JevokitApiAccessInterceptor(properties);
        registry.addInterceptor(interceptor)
                .addPathPatterns("/**")
                .order(Ordered.LOWEST_PRECEDENCE);
        log.info("API访问token控制器初始化完成");
    }
}
