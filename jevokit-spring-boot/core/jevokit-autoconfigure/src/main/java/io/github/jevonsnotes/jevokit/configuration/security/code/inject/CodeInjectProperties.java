package io.github.jevonsnotes.jevokit.configuration.security.code.inject;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.List;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2022/5/25 10:10
 * @Version 0.0.1
 */
@Data
@RefreshScope
@ConfigurationProperties(CodeInjectProperties.CONFIG_PREFIX)
public class CodeInjectProperties {
    public static final String CONFIG_PREFIX = "spkit.security.code-inject";
    public static final String DEFAULT_PATTERNS_NAMESPACE = "default";
    private Map<String,List<String>> namespaces;
    /**
     * 是否启用
     */
    private boolean enabled;
    /**
     * 决定正则表达式的匹配模式
     * 设置为false(默认)时，当规则匹配时，认定为安全，CodeInjectUtil.isSafe返回true
     * 设置为true时，当规则匹配时，认定为不安全，CodeInjectUtil.isSafe返回false
     */
    private boolean negative;


}
