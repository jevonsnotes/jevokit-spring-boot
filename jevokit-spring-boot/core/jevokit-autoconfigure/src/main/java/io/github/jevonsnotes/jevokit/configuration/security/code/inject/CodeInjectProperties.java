package io.github.jevonsnotes.jevokit.configuration.security.code.inject;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.List;
import java.util.Map;

/**
 * 代码注入识别配置（jevokit.security.code-inject）：按命名空间组织的正则规则集，供 CodeInjectUtil 判定内容安全性。
 * <p>namespaces 为"命名空间 → 正则列表"的映射，名为 default 的命名空间（{@link #DEFAULT_PATTERNS_NAMESPACE}）
 * 为缺省规则集；enabled 总开关；negative 反转判定极性（见字段注释）。
 * <p>@RefreshScope 支持配置中心热刷新规则。
 */
@Data
@RefreshScope
@ConfigurationProperties(CodeInjectProperties.CONFIG_PREFIX)
public class CodeInjectProperties {
    public static final String CONFIG_PREFIX = "jevokit.security.code-inject";
    public static final String DEFAULT_PATTERNS_NAMESPACE = "default";
    /** 命名空间到正则规则集的映射，default 为缺省命名空间 */
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
