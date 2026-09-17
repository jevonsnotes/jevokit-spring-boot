package io.github.jevonsnotes.jevokit.nogen.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jevonsnotes.jevokit.core.dto.SqlBundleDto;
import io.github.jevonsnotes.jevokit.core.util.SqlUtil;
import io.github.jevonsnotes.jevokit.nogen.consts.NGConsts;
import io.github.jevonsnotes.jevokit.nogen.po.BizNo;

import java.util.List;

 /**
 * NoGenDao 的 MySQL 方言实现：建表 SQL 与错误文案探测按 MySQL 语义提供。
 */
@RequiredArgsConstructor
public class MySQLDao implements NoGenDao {
    private final JdbcTemplate jdbcTemplate;


    @Override
    /**
    * MySQL 方言建表 SQL。
    * @return biz_no 表 DDL
    */
    public String getCreateSqlBizTable() {

        return "CREATE TABLE biz_no (" +
                "  biz_tag varchar(128)  primary key," +
                "  max_id int8 NOT NULL DEFAULT 1," +
                "  step int4 NOT NULL DEFAULT 100," +
                "  mode int2 NOT NULL DEFAULT 1," +
                "  threhold numeric(8,2) NOT NULL DEFAULT 100," +
                "  description varchar(500)  DEFAULT NULL," +
                "  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ");";
    }

    @Override
    /**
    * MySQL 环境校验语句。
    * @return 校验 SQL
    */
    public String validation() {
        return  "SELECT 1 FROM biz_no limit 1;";
    }

    @Override
    /**
    * MySQL"表不存在"报错匹配正则。
    * @return 正则串（对应 NGConsts.PATTERN_TABLE_NOT_EXISTS）
    */
    public String getPattenNotExists() {
        return NGConsts.PATTERN_TABLE_NOT_EXISTS;
    }


    @Override
    /**
    * 查全部单号配置（MySQL 方言）。
    * @return 配置行列表
    */
    public List<BizNo> getTags() {

        SqlBundleDto sqlBundleDto = SqlUtil.generateSelectSelectivePreSql(new BizNo(), new String[]{});
        return jdbcTemplate.query(sqlBundleDto.getSql(),new BeanPropertyRowMapper<>(BizNo.class),sqlBundleDto.getArgs());
    }

    @Override
    /**
    * 按主键选择性更新配置（MySQL 方言）。
    * @param bizNo 待更新字段载体
    * @return 影响行数
    */
    public int updateSelectiveByPrimaryKey(BizNo bizNo) {
        SqlBundleDto sqlBundleDto = SqlUtil.generateUpdateSelectivePreSql(bizNo, new String[]{"biz_tag"});
        return jdbcTemplate.update(sqlBundleDto.getSql(),sqlBundleDto.getArgs());
    }


}
