package io.github.jevonsnotes.jevokit.audit.log.dao;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogDto;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.core.dto.SqlBundleDto;
import io.github.jevonsnotes.jevokit.core.util.SqlUtil;

/**
 * 审计日志 DAO 契约：定义版本化审计表（ts_audit_log_v2 与其 HIS 历史表）的通用读写、
 * 按保留期的历史迁移逻辑，由各方言实现（MySQL/PostgreSQL/H2）提供 DDL 与校验 SQL。
 * 使用方式：由 JevokitAuditLogConfiguration 依 JDBC URL 选择实现注册为 bean；
 * 迁移由 {@code AuditLogAutoMigrateJob} 定时触发（INSERT 到 HIS 后 DELETE 主表，见 {@link #migrate()}）。
 * <p>关键约束：主表 primary key 为 local_key；migrate 以 MIGRATE_FLAG 标记行做并发互斥
 * （标记行存在即跳过本次迁移）；JdbcTemplate/配置经 static 惰性获取——理由见 {@link #jdbcTemplate()} 注释
 * （类初始化期 getBean 会形成加载顺序依赖，无 DB 环境直接炸装配）。
 */
public interface AuditLogDao extends BaseAutoDao{
    String VERSION = "v2";
    String TABLE_NAME = "ts_audit_log" + "_"+ VERSION;
    String TABLE_NAME_HIS = TABLE_NAME + "_HIS";
    String TABLE_PRIMARY_KEY = "local_key";
    String MIGRATE_FLAG = "MIGRATING";
    Logger log = LoggerFactory.getLogger(AuditLogDao.class);

    /**
     * 惰性获取 JdbcTemplate（原为接口 static 字段在类初始化期 getBean，形成类加载顺序依赖：
     * 无 DB/SpringUtil 未就绪时任何实现类加载即抛异常——终验裁定改为调用期查找）。
     * 每次调用直查，无共享可变状态；SpringUtil.getBean 自身即容器单例查找，成本可接受。
     */
    static JdbcTemplate jdbcTemplate() {
        return SpringUtil.getBean(JdbcTemplate.class);
    }

    /** 惰性获取审计配置（同 {@link #jdbcTemplate()} 的改造理由） */
    static AuditLogProperties properties() {
        return SpringUtil.getBean(AuditLogProperties.class);
    }

    default void createTableAuditLogHis() {
        String sql = "CREATE TABLE "+ TABLE_NAME_HIS +" AS" +
                " SELECT * FROM "+TABLE_NAME+" WHERE 1=0;";
        jdbcTemplate().execute(sql);
    }

    default void migrate() {

        DateTime date = DateUtil.date();

        switch (properties().getPersistent().getDbMigratePeriod()) {
            case YEAR:
                date = DateUtil.offset(date, DateField.YEAR,-1);
                break;
            case MONTH:
                date = DateUtil.offset(date, DateField.MONTH,-1);
                break;
            case DAY:
            default:
                date = DateUtil.offset(date, DateField.DAY_OF_YEAR,-1);
                break;

        }

        AuditLogDto auditLogDto = selectByKey(MIGRATE_FLAG);
        if(auditLogDto == null) {
            try {
                insert(AuditLogDto
                        .builder()
                        .globalKey(MIGRATE_FLAG)
                        .parentKey(MIGRATE_FLAG)
                        .localKey(MIGRATE_FLAG)
                        .eventType(MIGRATE_FLAG)
                        .eventName(MIGRATE_FLAG)
                        .build());
            } catch (Exception e) {
                return;
            }
            String insertSql =  "INSERT INTO "+ TABLE_NAME_HIS +
                    " SELECT * FROM "+TABLE_NAME+" WHERE start_time < ?;";

            int rowsInsert = jdbcTemplate().update(insertSql, date);
            log.info("audit log migrate rows {} successful",rowsInsert);


            String delete =  "DELETE FROM "+ TABLE_NAME +" WHERE  start_time < ?;";
            int rowsDeleted = jdbcTemplate().update(delete, date);
            log.info("audit log delete rows {} successful",rowsDeleted);

            deleteByKey(MIGRATE_FLAG);
        }

    } ;


    default int saveOrUpdate(AuditLogDto po) {

        AuditLogDto dbo = getById(TABLE_PRIMARY_KEY,po.getLocalKey());
        SqlBundleDto sqlBundleDto ;
        if(dbo != null){
            return updateSelectiveByPrimaryKey(po);
        } else {
            sqlBundleDto = SqlUtil.generateInsertSelectivePreSql(po);
            return jdbcTemplate().update(sqlBundleDto.getSql(),sqlBundleDto.getArgs());
        }
    }

    default int insert(AuditLogDto po) {
        SqlBundleDto sqlBundleDto = SqlUtil.generateInsertSelectivePreSql(po);
        return jdbcTemplate().update(sqlBundleDto.getSql(),sqlBundleDto.getArgs());
    }
    default int deleteByKey(String key) {
        return jdbcTemplate().update("DELETE FROM "+TABLE_NAME +" WHERE "+TABLE_PRIMARY_KEY+" = ?",key);
    }
    default AuditLogDto selectByKey(String key) {
        return getById(TABLE_PRIMARY_KEY,key);
    }


    default int updateSelectiveByPrimaryKey(AuditLogDto po) {
        SqlBundleDto sqlBundleDto = SqlUtil.generateUpdateSelectivePreSql(po, new String[]{TABLE_PRIMARY_KEY});
        return jdbcTemplate().update(sqlBundleDto.getSql(),sqlBundleDto.getArgs());
    }
}
