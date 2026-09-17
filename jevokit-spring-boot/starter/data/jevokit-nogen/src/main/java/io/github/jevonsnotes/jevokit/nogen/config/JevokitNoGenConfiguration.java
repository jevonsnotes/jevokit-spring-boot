package io.github.jevonsnotes.jevokit.nogen.config;

import cn.hutool.core.io.IoUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jevonsnotes.jevokit.core.consts.DBType;
import io.github.jevonsnotes.jevokit.nogen.api.JevokitNoGenApi;
import io.github.jevonsnotes.jevokit.nogen.dao.H2SQLDao;
import io.github.jevonsnotes.jevokit.nogen.dao.MySQLDao;
import io.github.jevonsnotes.jevokit.nogen.dao.NoGenDao;
import io.github.jevonsnotes.jevokit.nogen.dao.PostgreSQLDao;
import io.github.jevonsnotes.jevokit.nogen.helper.BzNoHelper;

import java.sql.Connection;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */
@Configuration
@Slf4j
public class JevokitNoGenConfiguration{
    @Bean
    public JevokitNoGenProperties jevokitNoGenProperties(){
        return new JevokitNoGenProperties();
    }
    @Bean
    public NoGenDao noGenDao(JdbcTemplate jdbcTemplate){
        return convert(getJdbcUrl(jdbcTemplate),jdbcTemplate);
    }

    @Bean
    public BzNoHelper bzNoHelper(JevokitNoGenProperties properties, NoGenDao noGenDao, RedissonClient client){
        return new BzNoHelper(properties,client,noGenDao);
    }
    @Bean
    public JevokitNoGenAutoInitDB jevokitNoGenAutoInitDB(JdbcTemplate jdbcTemplate,NoGenDao noGenDao,JevokitNoGenProperties properties){
        return new JevokitNoGenAutoInitDB(jdbcTemplate,noGenDao,properties);
    }
    @Bean
    public JevokitNoGenApi jevokitNoGenApi(BzNoHelper bzNoHelper){
        return new JevokitNoGenApi(bzNoHelper);
    }
    @Bean
    public JevokitNoGenWebConfiguration jevokitNoGenWebConfiguration(JevokitNoGenProperties properties){
        return new JevokitNoGenWebConfiguration(properties);
    }

    private String getJdbcUrl(JdbcTemplate jdbcTemplate){
        Connection connection = null;
        try {
            connection = jdbcTemplate.getDataSource().getConnection();
            return connection.getMetaData().getURL();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(),e);
        } finally {
            IoUtil.close(connection);
        }
    }
    private NoGenDao convert(String jdbcUrl,JdbcTemplate jdbcTemplate){
        if(jdbcUrl.matches(".*"+ DBType.MYSQL.getJdbcName()+":.*")){
            return new MySQLDao(jdbcTemplate);
        } else if(jdbcUrl.matches(".*"+ DBType.POSTGRESQL.getJdbcName()+":.*")){
            return new PostgreSQLDao(jdbcTemplate);
        }  else if(jdbcUrl.matches(".*"+ DBType.H2.getJdbcName()+":.*")){
            return new H2SQLDao(jdbcTemplate);
        } else {
            throw new UnsupportedOperationException("不支持的数据库类型");
        }
    }
}
