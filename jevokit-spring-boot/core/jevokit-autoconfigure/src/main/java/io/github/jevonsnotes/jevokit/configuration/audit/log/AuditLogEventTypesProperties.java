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
 * @Description TODO
 * @Date 2022/6/28 14:31
 * @Version 0.0.1
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
