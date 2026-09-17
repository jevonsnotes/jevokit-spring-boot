package io.github.jevonsnotes.jevokit.audit.log.dao;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogDto;
import io.github.jevonsnotes.jevokit.core.dto.SqlBundleDto;
import io.github.jevonsnotes.jevokit.core.util.SqlUtil;

import java.util.List;

/**
 * 审计 DAO 的方言无关基契约：建表、连通性校验 SQL 与"表不存在"异常模式，
 * 供自动建表（JevokitAuditLogAutoInitDB）以统一流程适配各方言。
 * 使用方式：不直接使用，由 {@link AuditLogDao} 继承；方言实现只提供 SQL 文本，
 * 通用查询（getById）在本层以默认方法实现。
 * <p>关键约束：getById 走 SpringUtil 惰性取 JdbcTemplate（调用期查找，避免类加载顺序依赖，
 * 理由同 AuditLogDao.jdbcTemplate()）；查询条件仅支持 localKey 等值 + 指定列名，是审计内部的窄接口，
 * 不要当通用 DAO 用。
 */
public interface BaseAutoDao {
    void createTableAuditLog();
    String validation();
    String validationHis();
    String getPattenNotExists();

    default AuditLogDto getById(String columnName,String localKey){
        JdbcTemplate jdbcTemplate = SpringUtil.getBean(JdbcTemplate.class);
        SqlBundleDto sqlBundleDto = SqlUtil.generateSelectSelectivePreSql(AuditLogDto.builder().localKey(localKey).build(), new String[]{columnName});
        List<AuditLogDto> pos = jdbcTemplate.query(sqlBundleDto.getSql(), new BeanPropertyRowMapper<>(AuditLogDto.class), sqlBundleDto.getArgs());
        return pos.size()>0 ? pos.get(0) : null;
    };

    int updateSelectiveByPrimaryKey(AuditLogDto po);

    int saveOrUpdate(AuditLogDto po);
}
