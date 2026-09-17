package io.github.jevonsnotes.jevokit.configuration.security.pathtraversal;

import lombok.Data;

/**
 * 路径穿越清洗的请求级元数据：由切面从 @PtClean 注解解析后经 {@code ConfigHolder} 绑定到当前线程，
 * 清洗过滤器据此在单次请求内覆盖全局 {@link PathTraversalProperties} 配置（enabled/mode）。
 */
@Data
public class PathTraversalMeta {
    boolean enabled;
    PathTraversalProperties.Mode mode;
}