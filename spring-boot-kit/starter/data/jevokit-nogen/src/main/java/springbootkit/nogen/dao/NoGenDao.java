package springbootkit.nogen.dao;

import cn.hutool.core.lang.Assert;
import cn.hutool.extra.spring.SpringUtil;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import springbootkit.core.dto.SqlBundleDto;
import springbootkit.core.util.SqlUtil;
import springbootkit.nogen.po.BizNo;

import java.util.List;

/**
 * @Description TODO
 * @Date 2023/3/10 16:25
 * @Version 0.0.1
 */
public interface NoGenDao {
    String getCreateSqlBizTable();
    String validation();
    String getPattenNotExists();

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
