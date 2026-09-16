package springbootkit.audit.log.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import springbootkit.audit.log.dao.AuditLogDao;
import springbootkit.configuration.audit.log.AuditLogProperties;
import springbootkit.core.consts.AuditLogPersistentMode;

/**
 * @Description TODO
 * @Date 2024/12/4
 * @Version 0.0.1
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
