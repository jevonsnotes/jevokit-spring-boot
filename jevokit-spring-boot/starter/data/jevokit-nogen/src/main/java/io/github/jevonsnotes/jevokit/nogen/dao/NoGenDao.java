package io.github.jevonsnotes.jevokit.nogen.dao;

import cn.hutool.core.lang.Assert;
import cn.hutool.extra.spring.SpringUtil;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jevonsnotes.jevokit.core.dto.SqlBundleDto;
import io.github.jevonsnotes.jevokit.core.util.SqlUtil;
import io.github.jevonsnotes.jevokit.nogen.po.BizNo;

import java.util.List;

 /**
 * 单号持久化 DAO 契约：建表 SQL / 库种校验 / 表不存在正则三件套由各库种实现，
 * getBizNo/getTags 等查询以 default 方法提供（经 SpringUtil 取 JdbcTemplate，SQL 通用）。
 */
public interface NoGenDao {
    String getCreateSqlBizTable();
    String validation();
    String getPattenNotExists();

    /**
    * 按业务标签查配置行（通用 SQL，方言无关）。
    * <p>遗留 #1 复核定性：调用期 SpringUtil.getBean 保持现状——仅由 BzNoHelper 在刷新完成后
    * 的业务线程调用（发号/配置回源），不构成 DEF-1 等待环；定性理由同
    * audit-log 的 AuditLogDao.jdbcTemplate()（惰性查找统一口径）。
    */
    default BizNo getBizNo(String bizTag){
        JdbcTemplate jdbcTemplate = SpringUtil.getBean(JdbcTemplate.class);
        SqlBundleDto sqlBundleDto = SqlUtil.generateSelectSelectivePreSql(new BizNo().setBizTag(bizTag), new String[]{"biz_tag"});
        List<BizNo> pos = jdbcTemplate.query(sqlBundleDto.getSql(), new BeanPropertyRowMapper<>(BizNo.class), sqlBundleDto.getArgs());
        Assert.isTrue(!pos.isEmpty(),"单号配置不存在");
        return pos.get(0);
    };
    List<BizNo> getTags();

    int updateSelectiveByPrimaryKey(BizNo bizNo);
}
