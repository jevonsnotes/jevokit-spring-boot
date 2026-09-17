package io.github.jevonsnotes.jevokit.audit.log.dao;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogDto;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.core.consts.DBConst;
import io.github.jevonsnotes.jevokit.core.dto.SqlBundleDto;
import io.github.jevonsnotes.jevokit.core.util.SqlUtil;

/**
 * @Description TODO
 * @Date 2023/3/10 16:28
 * @Version 0.0.1
 */
@Slf4j
@RequiredArgsConstructor
public class H2SQLDao implements AuditLogDao {
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogProperties properties;

    @Override
    public void createTableAuditLog() {
        String sql = "CREATE TABLE "+ TABLE_NAME +" (" +
                "  global_key varchar(64) NOT NULL," +
                "  parent_key varchar(64) NOT NULL," +
                "  "+TABLE_PRIMARY_KEY+" varchar(64) NOT NULL," +
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
                "  start_time timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  end_time timestamp(6) ," +
                "  cost int8 ," +
                "  query_string CLOB ," +
                "  request_body CLOB ," +
                "  response_body CLOB ," +
                "  CONSTRAINT IDX_"+TABLE_NAME+"_pkey PRIMARY KEY ("+TABLE_PRIMARY_KEY+")" +
                ");";
        jdbcTemplate.execute(sql);
    }


    @Override
    public String validation() {
        return "SELECT 1 FROM "+ TABLE_NAME +" limit 1;";
    }
    @Override
    public String validationHis() {
        return "SELECT 1 FROM "+ TABLE_NAME_HIS +" limit 1;";
    }

    @Override
    public String getPattenNotExists() {
        return DBConst.PATTERN_TABLE_NOT_EXISTS_H2;
    }




}
