package io.github.jevonsnotes.jevokit.configuration.audit.log;

/**
 * 审计表自动转储周期粒度：DB 持久化模式下审计记录按该粒度分表归档（如按天则表名带日期后缀），
 * 由 spkit.audit.log.persistent.db-migrate-period 配置，默认 DAY。
 */
public enum AuditLogAutoMigratePeriod {
    YEAR,MONTH,DAY
}
