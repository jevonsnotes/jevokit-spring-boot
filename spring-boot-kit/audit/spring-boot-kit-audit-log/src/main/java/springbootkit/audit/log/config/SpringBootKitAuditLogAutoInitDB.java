package springbootkit.audit.log.config;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import springbootkit.audit.log.dao.AuditLogDao;
import springbootkit.configuration.audit.log.AuditLogProperties;
import springbootkit.core.util.ExceptionUtil;

/**
 * @Description 初始化
 * @Date 2023/3/10 16:09
 * @Version 0.0.1
 */
@Slf4j
@RequiredArgsConstructor
public class SpringBootKitAuditLogAutoInitDB implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogDao dao;
    private final AuditLogProperties properties;
    /**
     * Callback used to run the bean.
     *
     * @param args incoming application arguments
     * @throws Exception on error
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("****** 审计日志组件初始化开始 ******");
        initTableAuditLog();
        initTableAuditLogHis();
        log.info("****** 审计日志组件初始化结束 ******");
    }

    private void initTableAuditLog(){
        try {
            jdbcTemplate.queryForList(dao.validation());
        } catch (DataAccessException e) {
            if(ExceptionUtil.loopMatchMessage(e,dao.getPattenNotExists())){
                dao.createTableAuditLog();
            } else {
                throw e;
            }
        }
    }
    private void initTableAuditLogHis(){
        try {
            jdbcTemplate.queryForList(dao.validationHis());
        } catch (DataAccessException e) {
            if(ExceptionUtil.loopMatchMessage(e,dao.getPattenNotExists())){
                dao.createTableAuditLogHis();
            } else {
                throw e;
            }
        }
    }

}
