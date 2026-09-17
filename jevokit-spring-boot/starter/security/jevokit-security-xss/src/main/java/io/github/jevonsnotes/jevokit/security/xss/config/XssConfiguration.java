package io.github.jevonsnotes.jevokit.security.xss.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;
import io.github.jevonsnotes.jevokit.core.editor.StringPropertiesEditor;
import io.github.jevonsnotes.jevokit.security.xss.chain.XssStringPropertiesEditChain;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.DefaultXssCleaner;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;
import io.github.jevonsnotes.jevokit.security.xss.handler.FormXssHandler;
import io.github.jevonsnotes.jevokit.security.xss.handler.JsonXssHandler;
import io.github.jevonsnotes.jevokit.security.xss.handler.XmlXssHandler;
import io.github.jevonsnotes.jevokit.security.xss.handler.ResponseXssHandler;
import io.github.jevonsnotes.jevokit.security.xss.interceptor.XssCleanInterceptor;

import java.util.List;

@RequiredArgsConstructor
@EnableConfigurationProperties(XssProperties.class)
@ConditionalOnProperty(
        prefix = XssProperties.XSS_CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration
@Slf4j
public class XssConfiguration implements WebMvcConfigurer {

    private final XssProperties xssProperties;


    /**
     * 注册默认清洗器（业务可用自定义 XssCleaner bean 覆盖）。
     */
    @Bean
    @ConditionalOnMissingBean
    public XssCleaner xssCleaner(XssProperties properties) {
        return new DefaultXssCleaner(properties);
    }

    /** 注册表单绑定清洗处理器。 */
    @Bean
    public FormXssHandler formXssHandler(StringPropertiesEditor editor) {
        return new FormXssHandler(editor);
    }

    /** 注册字符串属性编辑链节点（表单 String 属性清洗的一环）。 */
    @Bean
    public XssStringPropertiesEditChain xssStringPropertiesEditChain(XssProperties properties,
                                                                     XssCleaner xssCleaner) {
        return new XssStringPropertiesEditChain(xssCleaner,properties);
    }


    /** 注册响应体清洗处理器（受 ignore-response 开关控制）。 */
    @Bean
    public ResponseXssHandler responseXssHandler(XssCleaner xssCleaner) {
        return new ResponseXssHandler( xssCleaner);
    }
    /** 注册 XML 报文清洗处理器。 */
    @Bean
    public XmlXssHandler xmlXssHandler(XssCleaner xssCleaner) {
        return new XmlXssHandler( xssCleaner);
    }


    /**
     * 向全局 ObjectMapper 注册 String 反序列化清洗（JSON body 的每个字符串字段生效）。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer xssJacksonCustomizer(XssProperties properties,
                                                                      XssCleaner xssCleaner) {
        return builder -> builder.deserializerByType(String.class, new JsonXssHandler(properties, xssCleaner));
    }


    /**
     * 注册 XSS 拦截器：按 url-patterns（默认 /**）为命中请求写入防护 ThreadLocal，
     * 请求结束清理（排除 url-exclude-patterns）。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> urlPatterns = xssProperties.getUrlPatterns();
        if (urlPatterns.isEmpty()) {
            urlPatterns.add("/**");
        }
        XssCleanInterceptor interceptor = new XssCleanInterceptor(xssProperties);
        registry.addInterceptor(interceptor)
                .addPathPatterns(urlPatterns)
                .excludePathPatterns(xssProperties.getUrlExcludePatterns())
                .order(Ordered.LOWEST_PRECEDENCE);
        log.info("Defender is ready! [{}]","xss");
    }

}
