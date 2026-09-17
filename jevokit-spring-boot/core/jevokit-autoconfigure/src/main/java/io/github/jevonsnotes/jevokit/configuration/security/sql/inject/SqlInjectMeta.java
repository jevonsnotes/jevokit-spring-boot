package io.github.jevonsnotes.jevokit.configuration.security.sql.inject;

import lombok.Data;

/**
 * SQL 注入清洗的请求级元数据：由切面从 @SIClean 注解解析后经 {@code ConfigHolder} 绑定到当前线程，
 * 清洗过滤器据此在单次请求内覆盖全局 {@link SqlInjectProperties} 配置（enabled/mode）。
 */
@Data
public class SqlInjectMeta {
    boolean enabled;
    SqlInjectProperties.Mode mode;
}