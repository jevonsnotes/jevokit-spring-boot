package springbootkit.audit.log.dao;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import springbootkit.audit.log.dto.AuditLogDto;
import springbootkit.core.dto.SqlBundleDto;
import springbootkit.core.util.SqlUtil;

import java.util.List;

/**
 * @Description TODO
 * @Date 2023/3/10 16:25
 * @Version 0.0.1
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
