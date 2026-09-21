package io.github.jevonsnotes.jevokit.security.code.inject.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.configuration.security.code.inject.CodeInjectProperties;
import io.github.jevonsnotes.jevokit.security.code.inject.util.CodeInjectUtil;

/**
 * 动态代码注入校验的装配类：注册 {@link CodeInjectUtil} bean 并驱动其配置注入。
 * 使用方式：引入本 starter 即生效（jevokit.security.code-inject.enabled 未配置时默认开启，matchIfMissing=true），
 * 之后业务侧静态调用 CodeInjectUtil.isSafe / errorIfUnsafe 即可。
 * <p>关键约束：enabled=false 时本配置类整体跳过、CodeInjectUtil 不注册——其静态方法因配置未注入而不可用
 * （详见 CodeInjectUtil 类注释），需要校验能力时不要关闭该开关。
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

    /** 注册校验工具 bean（驱动静态配置注入，见 CodeInjectUtil 类注释的初始化约束）。 */
    @Bean
    public CodeInjectUtil dynamicCodeInjectUtil(){
        return new CodeInjectUtil();
    }

}
