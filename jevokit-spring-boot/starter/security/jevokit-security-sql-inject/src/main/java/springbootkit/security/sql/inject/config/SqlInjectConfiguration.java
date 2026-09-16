package springbootkit.security.sql.inject.config;

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
import springbootkit.configuration.security.sql.inject.SqlInjectProperties;
import springbootkit.core.editor.StringPropertiesEditor;
import springbootkit.security.sql.inject.chain.SqlInjectStringPropertiesEditChain;
import springbootkit.security.sql.inject.handler.FormSqlInjectHandler;
import springbootkit.security.sql.inject.handler.RequestBodySqlInjectHandler;
import springbootkit.security.sql.inject.interceptor.SqlInjectInterceptor;

import java.util.List;

@RequiredArgsConstructor
@EnableConfigurationProperties(SqlInjectProperties.class)
@ConditionalOnProperty(
        prefix = SqlInjectProperties.CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration
@Slf4j
public class SqlInjectConfiguration implements WebMvcConfigurer {
    private final SqlInjectProperties properties;


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
        SqlInjectInterceptor interceptor = new SqlInjectInterceptor(properties);
        registry.addInterceptor(interceptor)
                .addPathPatterns(urlPatterns)
                .excludePathPatterns(properties.getUrlExcludePatterns())
                .order(Ordered.LOWEST_PRECEDENCE);
        log.info("Defender is ready! [{}]","sql inject");
    }
    @Bean
    public RequestBodySqlInjectHandler requestBodySqlInjectHandler(){
        return new RequestBodySqlInjectHandler(properties);
    }
    @Bean
    public FormSqlInjectHandler formSqlInjectHandler(StringPropertiesEditor editor){
        return new FormSqlInjectHandler(editor);
    }

    @Bean
    public SqlInjectStringPropertiesEditChain sqlInjectStringPropertiesEditChain(){
        return new SqlInjectStringPropertiesEditChain(properties);
    }

}
