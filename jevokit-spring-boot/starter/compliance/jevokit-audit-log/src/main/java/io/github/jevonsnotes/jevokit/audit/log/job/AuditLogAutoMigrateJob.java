package io.github.jevonsnotes.jevokit.audit.log.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import io.github.jevonsnotes.jevokit.audit.log.dao.AuditLogDao;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.core.consts.AuditLogPersistentMode;

/**
 * 审计日志自动迁移定时任务：每天 01:00 按保留期（db-migrate-period：日/月/年）把主表
 * 过期数据迁入 HIS 历史表并从主表删除，防止审计表无限膨胀。
 * 使用方式：随 starter 装配自动生效（依赖存在 AuditLogDao bean，即已配置数据库），
 * 无需手工触发；保留期经 spkit.audit-log.persistent.db-migrate-period 配置。
 * <p>关键约束：仅在持久化模式为 DB 时执行；迁移的并发互斥由 AuditLogDao.migrate 的
 * MIGRATE_FLAG 标记行保证（标记行存在即跳过本次），多实例部署也安全。
 */
@EnableScheduling
@RequiredArgsConstructor
public class AuditLogAutoMigrateJob {
    private final AuditLogDao dao;
    private final AuditLogProperties auditLogProperties;

    @Scheduled(cron = "0 0 1 * * ?")
    public void execute(){
        if(auditLogProperties.getPersistent().getMode() == AuditLogPersistentMode.DB){
            dao.migrate();
        }
    }
}
