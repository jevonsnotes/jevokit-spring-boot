package springbootkit.nogen.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springbootkit.nogen.interceptor.NoGenInterceptor;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */

@Slf4j
@RequiredArgsConstructor
public class SpringBootKitNoGenWebConfiguration  implements WebMvcConfigurer {
    private final SpringBootKitNoGenProperties properties;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        NoGenInterceptor interceptor = new NoGenInterceptor(properties);
        registry.addInterceptor(interceptor)
                .addPathPatterns("/spring-boot-kit/nogen/**")
                .order(Ordered.LOWEST_PRECEDENCE);
        log.info("单号服务拦截器初始化完成");
    }
}
