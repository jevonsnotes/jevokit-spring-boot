package io.github.jevonsnotes.jevokit.configuration.audit.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import io.github.jevonsnotes.jevokit.core.consts.AuditLogPersistentMode;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计事件类型配置（spkit.audit.log.event-types.*）：
 * nameMapper 维护事件代码到展示名的映射（如 QUERY → 查询）；
 * patterns 按 HTTP 方法分组、以 AntPathMatcher 路径模式识别请求归属的事件类型，
 * 未命中任何模式的请求不产生审计事件。
 */
@Data
public class AuditLogEventTypesProperties {
    /**
     * 事件类型名称映射，如 QUERY: 查询
     */
    private Map<String, String> nameMapper = new HashMap<>();
    /**
     * 事件识别表达式，基于AntPathMatcher路径识别
     */
    private Map<HttpMethod, Map<String,List<String>>> patterns = new HashMap<>();

}
