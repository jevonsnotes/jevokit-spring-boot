package io.github.jevonsnotes.jevokit.security.sql.inject.config;

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
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties;
import io.github.jevonsnotes.jevokit.core.editor.StringPropertiesEditor;
import io.github.jevonsnotes.jevokit.security.sql.inject.chain.SqlInjectStringPropertiesEditChain;
import io.github.jevonsnotes.jevokit.security.sql.inject.handler.FormSqlInjectHandler;
import io.github.jevonsnotes.jevokit.security.sql.inject.handler.RequestBodySqlInjectHandler;
import io.github.jevonsnotes.jevokit.security.sql.inject.interceptor.SqlInjectInterceptor;

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
     * 注册 SQL 注入拦截器：按 url-patterns（默认 /**）为命中请求写入防护 ThreadLocal，
     * 请求结束清理（排除 url-exclude-patterns）。
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
    /** 注册请求体清洗处理器（ThreadLocal 开启时生效）。 */
    @Bean
    public RequestBodySqlInjectHandler requestBodySqlInjectHandler(){
        return new RequestBodySqlInjectHandler(properties);
    }
    /** 注册表单绑定清洗处理器。 */
    @Bean
    public FormSqlInjectHandler formSqlInjectHandler(StringPropertiesEditor editor){
        return new FormSqlInjectHandler(editor);
    }

    /** 注册字符串属性编辑链节点（表单 String 属性清洗的一环）。 */
    @Bean
    public SqlInjectStringPropertiesEditChain sqlInjectStringPropertiesEditChain(){
        return new SqlInjectStringPropertiesEditChain(properties);
    }

}
