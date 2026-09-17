package io.github.jevonsnotes.jevokit.security.code.inject.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.configuration.security.code.inject.CodeInjectProperties;
import io.github.jevonsnotes.jevokit.security.code.inject.util.CodeInjectUtil;

/**
 * @Description TODO
 * @Date 2022/5/25 10:43
 * @Version 0.0.1
 */
@EnableConfigurationProperties(CodeInjectProperties.class)
@ConditionalOnProperty(
        prefix = CodeInjectProperties.CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Configuration
@Slf4j
public class CodeInjectConfiguration {

    @Bean
    public CodeInjectUtil dynamicCodeInjectUtil(){
        return new CodeInjectUtil();
    }

}
