package io.github.jevonsnotes.jevokit.excel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.github.jevonsnotes.jevokit.excel.resolver.ExcelResolver;

import java.util.List;

/**
 * excel 导入自动装配：注册 ExcelResolver 为 HandlerMethodArgumentResolver，
 * 使 Controller 方法可直接声明 @Excel List&lt;T&gt; 入参（multipart 文件或 JSON 数组
 * 统一解析绑定）。仅 SERVLET web 环境生效。
 */

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration
@Slf4j
public class JevokitExcelConfiguration implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ExcelResolver());
    }
}
