package io.github.jevonsnotes.jevokit.configuration.security.xss;

import lombok.Data;

/**
 * XSS 清洗的请求级元数据：由切面从 @XssClean 注解解析后经 {@code ConfigHolder} 绑定到当前线程，
 * 清洗过滤器据此在单次请求内覆盖全局 {@link XssProperties} 配置（enabled/mode/ignoreResponse）。
 */
@Data
public class XssMeta {
    boolean enabled;
    XssProperties.Mode mode;
    boolean ignoreResponse = true;
}