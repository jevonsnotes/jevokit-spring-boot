package springbootkit.nogen.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import springbootkit.core.dto.SqlBundleDto;
import springbootkit.core.util.SqlUtil;
import springbootkit.nogen.consts.NGConsts;
import springbootkit.nogen.po.BizNo;

import java.util.List;

/**
 * @Description TODO
 * @Date 2023/3/10 16:28
 * @Version 0.0.1
 */
@RequiredArgsConstructor
public class PostgreSQLDao implements NoGenDao {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public String getCreateSqlBizTable() {
        return "CREATE TABLE biz_no (" +
                "  biz_tag varchar(128) NOT NULL," +
                "  max_id int8 NOT NULL DEFAULT 1," +
                "  step int4 NOT NULL DEFAULT 100," +
                "  mode int2 NOT NULL DEFAULT 1," +
                "  threhold numeric(8,2) NOT NULL DEFAULT 100," +
                "  description varchar(500) DEFAULT NULL," +
                "  create_time timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  update_time timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  CONSTRAINT biz_no_pkey PRIMARY KEY (biz_tag)" +
                ");";
    }

    @Override
    public String validation() {
        return "SELECT 1 FROM biz_no limit 1;";
    }
    @Override
    public String getPattenNotExists() {
        return NGConsts.PATTERN_TABLE_NOT_EXISTS_PG;
    }


    @Override
    public List<BizNo> getTags() {

        SqlBundleDto sqlBundleDto = SqlUtil.generateSelectSelectivePreSql(new BizNo(), new String[]{});
        return jdbcTemplate.query(sqlBundleDto.getSql(),new BeanPropertyRowMapper<>(BizNo.class),sqlBundleDto.getArgs());
    }

    @Override
    public int updateSelectiveByPrimaryKey(BizNo bizNo) {
        SqlBundleDto sqlBundleDto = SqlUtil.generateUpdateSelectivePreSql(bizNo, new String[]{"biz_tag"});
        return jdbcTemplate.update(sqlBundleDto.getSql(),sqlBundleDto.getArgs());
    }
}
