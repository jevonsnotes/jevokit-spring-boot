package springbootkit.security.pt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springbootkit.configuration.security.pathtraversal.PathTraversalProperties;
import springbootkit.core.editor.StringPropertiesEditor;
import springbootkit.security.pt.chain.PtStringPropertiesEditChain;
import springbootkit.security.pt.handler.FormPathTraversalHandler;
import springbootkit.security.pt.handler.RequestBodyPathTraversalHandler;
import springbootkit.security.pt.interceptor.PathTraversalInterceptor;

import java.util.List;

@RequiredArgsConstructor
@EnableConfigurationProperties(PathTraversalProperties.class)
@ConditionalOnProperty(
        prefix = PathTraversalProperties.PT_CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration
@Slf4j
public class PathTraversalConfiguration implements WebMvcConfigurer {
    private final PathTraversalProperties properties;


    /**
     * Add Spring MVC lifecycle interceptors for pre- and post-processing of
     * controller method invocations and resource handler requests.
     * Interceptors can be registered to apply to all requests or be limited
     * to a subset of URL patterns.
     *
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> urlPatterns = properties.getUrlPatterns();
        if (urlPatterns.isEmpty()) {
            urlPatterns.add("/**");
        }
        PathTraversalInterceptor interceptor = new PathTraversalInterceptor(properties);
        registry.addInterceptor(interceptor)
                .addPathPatterns(urlPatterns)
                .excludePathPatterns(properties.getUrlExcludePatterns())
                .order(Ordered.LOWEST_PRECEDENCE);
        log.info("Defender is ready! [{}]","path traversal");
    }
    @Bean
    public RequestBodyPathTraversalHandler pathTraversalHandler(){
        return new RequestBodyPathTraversalHandler(properties);
    }
    @Bean
    public FormPathTraversalHandler formPathTraversalHandler(StringPropertiesEditor editor){
        return new FormPathTraversalHandler(editor);
    }

    @Bean
    public PtStringPropertiesEditChain ptStringPropertiesEditChain(){
        return new PtStringPropertiesEditChain(properties);
    }

}
