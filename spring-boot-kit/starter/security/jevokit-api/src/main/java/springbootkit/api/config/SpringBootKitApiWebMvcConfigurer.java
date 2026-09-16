package springbootkit.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springbootkit.api.interceptor.SpringBootKitApiAccessInterceptor;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */

@Slf4j
@RequiredArgsConstructor
public class SpringBootKitApiWebMvcConfigurer implements WebMvcConfigurer {
    private final SpringBootKitApiProperties properties;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        SpringBootKitApiAccessInterceptor interceptor = new SpringBootKitApiAccessInterceptor(properties);
        registry.addInterceptor(interceptor)
                .addPathPatterns("/**")
                .order(Ordered.LOWEST_PRECEDENCE);
        log.info("API访问token控制器初始化完成");
    }
}
