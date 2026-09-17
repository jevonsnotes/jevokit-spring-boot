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


    @Bean
    @ConditionalOnMissingBean
    public XssCleaner xssCleaner(XssProperties properties) {
        return new DefaultXssCleaner(properties);
    }

    @Bean
    public FormXssHandler formXssHandler(StringPropertiesEditor editor) {
        return new FormXssHandler(editor);
    }

    @Bean
    public XssStringPropertiesEditChain xssStringPropertiesEditChain(XssProperties properties,
                                                                     XssCleaner xssCleaner) {
        return new XssStringPropertiesEditChain(xssCleaner,properties);
    }


    @Bean
    public ResponseXssHandler responseXssHandler(XssCleaner xssCleaner) {
        return new ResponseXssHandler( xssCleaner);
    }
    @Bean
    public XmlXssHandler xmlXssHandler(XssCleaner xssCleaner) {
        return new XmlXssHandler( xssCleaner);
    }


    @Bean
    public Jackson2ObjectMapperBuilderCustomizer xssJacksonCustomizer(XssProperties properties,
                                                                      XssCleaner xssCleaner) {
        return builder -> builder.deserializerByType(String.class, new JsonXssHandler(properties, xssCleaner));
    }


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
