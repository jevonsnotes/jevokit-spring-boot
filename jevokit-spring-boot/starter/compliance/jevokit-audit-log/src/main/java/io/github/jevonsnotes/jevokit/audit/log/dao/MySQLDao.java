package io.github.jevonsnotes.jevokit.audit.log.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.core.consts.DBConst;

/**
 * 审计日志 MySQL 方言实现：提供审计表 DDL、连通性校验 SQL 与"表不存在"异常匹配模式。
 * 使用方式：由 JevokitAuditLogConfiguration 依 JDBC URL 自动选择注册（jdbcUrl 含 mysql:），
 * 业务不直接实例化。
 * <p>关键约束：大字段用 TEXT、时间列用 timestamp 默认 CURRENT_TIMESTAMP；
 * {@code migrate()} 覆写为空实现——MySQL 侧不做自动历史迁移（保留接口默认行为给需要的方言）。
 */
@RequiredArgsConstructor
public class MySQLDao implements AuditLogDao {
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogProperties properties;


    @Override
    public void createTableAuditLog() {
        String sql = "CREATE TABLE "+ TABLE_NAME +" (" +
                "  global_key varchar(64) NOT NULL," +
                "  parent_key varchar(64) NOT NULL," +
                "  "+TABLE_PRIMARY_KEY+" varchar(64) NOT NULL primary key," +
                "  event_type varchar(128) NOT NULL," +
                "  event_name varchar(255) ," +
                "  user_code varchar(64) ," +
                "  com_code varchar(32) ," +
                "  source_ip varchar(255) ," +
                "  local_ip varchar(255) ," +
                "  port int8 ," +
                "  scheme varchar(255) ," +
                "  method varchar(255) ," +
                "  class_method varchar(255) ," +
                "  class_name varchar(255) ," +
                "  content_type varchar(255) ," +
                "  servlet_path varchar(500) ," +
                "  status varchar(20) ," +
                "  start_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  end_time timestamp ," +
                "  cost int8 " +
                "  query_string TEXT ," +
                "  request_body TEXT ," +
                "  response_body TEXT ," +
                ");";
        jdbcTemplate.execute(sql);
    }

    @Override
    public String validation() {
        return  "SELECT 1 FROM "+ TABLE_NAME +" limit 1;";
    }

    @Override
    public String validationHis() {
        return "SELECT 1 FROM "+ TABLE_NAME_HIS +" limit 1;";
    }

    @Override
    public String getPattenNotExists() {
        return DBConst.PATTERN_TABLE_NOT_EXISTS;
    }


    @Override
    public void migrate() {
    }
}
