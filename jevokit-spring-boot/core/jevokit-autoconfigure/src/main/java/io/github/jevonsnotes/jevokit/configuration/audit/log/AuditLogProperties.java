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
 * 审计日志总配置（配置前缀 spkit.audit.log）：总开关、MDC/环绕模式、事件类型映射与持久化策略的聚合根。
 * <p>常用方式：在 application.yml 中按前缀配置；@RefreshScope 支持配置中心运行时热刷新。
 * 默认整体关闭（enabled=false），需显式开启；ignoreNonUser 打开后 usercode 为空的请求不入审计（过滤探活/内部调用）。
 * 子项细节见 {@link AuditLogContentLimitProperties}、{@link AuditLogEventTypesProperties}、{@link AuditLogPersistentProperties}。
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
