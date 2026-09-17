package io.github.jevonsnotes.jevokit.configuration.audit.log;

import lombok.Data;
import io.github.jevonsnotes.jevokit.core.consts.AuditLogPersistentMode;

/**
 * 审计日志持久化配置（spkit.audit.log.persistent.*）：
 * mode 选择落盘去向（LOG 日志 / DB 数据库 / MIXED 双写，默认 LOG，语义见 {@link AuditLogPersistentMode}）；
 * dbMigratePeriod 仅 DB 模式生效，决定审计表按什么粒度分表转储（默认按天）。
 */
@Data
public class AuditLogPersistentProperties {
    /**
     * 持久化模式 LOG日志 DB数据库 MIXED混合
     */
    private AuditLogPersistentMode mode =  AuditLogPersistentMode.LOG;
    /**
     * 持久化模式为DB时，设置自动转储间隔
     */
    private AuditLogAutoMigratePeriod dbMigratePeriod = AuditLogAutoMigratePeriod.DAY;

}
