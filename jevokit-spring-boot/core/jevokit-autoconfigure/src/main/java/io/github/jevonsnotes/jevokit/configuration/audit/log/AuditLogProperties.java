package io.github.jevonsnotes.jevokit.configuration.audit.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import io.github.jevonsnotes.jevokit.core.consts.AuditLogPersistentMode;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;

import java.util.List;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2022/6/28 14:31
 * @Version 0.0.1
 */
@Data
@RefreshScope
@ConfigurationProperties(AuditLogProperties.CONFIG_PREFIX)
public class AuditLogProperties {
    public static final String CONFIG_PREFIX = "spkit.audit.log";
    /**
     * 启用审计日志，默认false
     */
    private boolean enabled;
    /**
     * 启用mdc记录 默认false
     */
    private boolean enabledMdc;
    /**
     * 启用环绕审计，将会在请求处理前后调用save方法各一次
     */
    private boolean enabledAround;
    /**
     * 忽略非用户操作记录
     * usercode为空则判定为非用户操作
     */
    private boolean ignoreNonUser;

    @NestedConfigurationProperty
    private AuditLogContentLimitProperties contentLimit= new AuditLogContentLimitProperties();

    /**
     * 事件类型
     */
    @NestedConfigurationProperty
    private AuditLogEventTypesProperties eventTypes = new AuditLogEventTypesProperties();

    /**
     * 持久化模式 LOG日志 DB数据库 MIXED混合
     */
    @NestedConfigurationProperty
    private AuditLogPersistentProperties persistent =  new AuditLogPersistentProperties();


}
