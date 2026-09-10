package springbootkit.audit.log.dao;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import springbootkit.audit.log.dto.AuditLogDto;
import springbootkit.configuration.audit.log.AuditLogProperties;
import springbootkit.core.dto.SqlBundleDto;
import springbootkit.core.util.SqlUtil;

/**
 * @Description TODO
 * @Date 2023/3/10 16:25
 * @Version 0.0.1
 */
public interface AuditLogDao extends BaseAutoDao{
    String VERSION = "v2";
    String TABLE_NAME = "ts_audit_log" + "_"+ VERSION;
    String TABLE_NAME_HIS = TABLE_NAME + "_HIS";
    String TABLE_PRIMARY_KEY = "local_key";
    String MIGRATE_FLAG = "MIGRATING";
    JdbcTemplate jdbcTemplate = SpringUtil.getBean(JdbcTemplate.class);
    AuditLogProperties PROPERTIES = SpringUtil.getBean(AuditLogProperties.class);
    Logger log = LoggerFactory.getLogger(AuditLogDao.class);


    default void createTableAuditLogHis() {
        String sql = "CREATE TABLE "+ TABLE_NAME_HIS +" AS" +
                " SELECT * FROM "+TABLE_NAME+" WHERE 1=0;";
        jdbcTemplate.execute(sql);
    }

    default void migrate() {

        DateTime date = DateUtil.date();

        switch (PROPERTIES.getPersistent().getDbMigratePeriod()) {
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

            int rowsInsert = jdbcTemplate.update(insertSql, date);
            log.info("audit log migrate rows {} successful",rowsInsert);


            String delete =  "DELETE FROM "+ TABLE_NAME +" WHERE  start_time < ?;";
            int rowsDeleted = jdbcTemplate.update(delete, date);
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
            return jdbcTemplate.update(sqlBundleDto.getSql(),sqlBundleDto.getArgs());
        }
    }

    default int insert(AuditLogDto po) {
        SqlBundleDto sqlBundleDto = SqlUtil.generateInsertSelectivePreSql(po);
        return jdbcTemplate.update(sqlBundleDto.getSql(),sqlBundleDto.getArgs());
    }
    default int deleteByKey(String key) {
        return jdbcTemplate.update("DELETE FROM "+TABLE_NAME +" WHERE "+TABLE_PRIMARY_KEY+" = ?",key);
    }
    default AuditLogDto selectByKey(String key) {
        return getById(TABLE_PRIMARY_KEY,key);
    }


    default int updateSelectiveByPrimaryKey(AuditLogDto po) {
        SqlBundleDto sqlBundleDto = SqlUtil.generateUpdateSelectivePreSql(po, new String[]{TABLE_PRIMARY_KEY});
        return jdbcTemplate.update(sqlBundleDto.getSql(),sqlBundleDto.getArgs());
    }
}
