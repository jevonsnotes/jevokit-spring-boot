package io.github.jevonsnotes.jevokit.nogen.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.github.jevonsnotes.jevokit.nogen.interceptor.NoGenInterceptor;

 /**
 * nogen Web 装配：注册 NoGenInterceptor 到 /jevokit/nogen/**（最低优先级，令业务拦截器
 * 先行），对发号 API 做 accessToken 校验。enableApi=false 时拦截器空转（无 API 可拦）。
 */

@Slf4j
@RequiredArgsConstructor
public class JevokitNoGenWebConfiguration  implements WebMvcConfigurer {
    private final JevokitNoGenProperties properties;
    @Override
    /**
    * 注册 NoGenInterceptor 到 /jevokit/nogen/**（LOWEST_PRECEDENCE，业务拦截器先行）。
    * @param registry 拦截器注册器
    */
    public void addInterceptors(InterceptorRegistry registry) {

        NoGenInterceptor interceptor = new NoGenInterceptor(properties);
        registry.addInterceptor(interceptor)
                .addPathPatterns("/jevokit/nogen/**")
                .order(Ordered.LOWEST_PRECEDENCE);
        log.info("单号服务拦截器初始化完成");
    }
}
