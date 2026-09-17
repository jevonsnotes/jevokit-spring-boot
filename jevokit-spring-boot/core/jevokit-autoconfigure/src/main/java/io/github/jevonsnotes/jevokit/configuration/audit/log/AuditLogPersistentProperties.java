package io.github.jevonsnotes.jevokit.configuration.audit.log;

import lombok.Data;
import io.github.jevonsnotes.jevokit.core.consts.AuditLogPersistentMode;

/**
 * @Description TODO
 * @Date 2024/12/4
 * @Version 0.0.1
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
