package springbootkit.nogen.config;

import cn.hutool.core.io.IoUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import springbootkit.core.consts.DBType;
import springbootkit.nogen.api.SpringBootKitNoGenApi;
import springbootkit.nogen.dao.H2SQLDao;
import springbootkit.nogen.dao.MySQLDao;
import springbootkit.nogen.dao.NoGenDao;
import springbootkit.nogen.dao.PostgreSQLDao;
import springbootkit.nogen.helper.BzNoHelper;

import java.sql.Connection;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */
@Configuration
@Slf4j
public class SpringBootKitNoGenConfiguration{
    @Bean
    public SpringBootKitNoGenProperties springBootKitNoGenProperties(){
        return new SpringBootKitNoGenProperties();
    }
    @Bean
    public NoGenDao noGenDao(JdbcTemplate jdbcTemplate){
        return convert(getJdbcUrl(jdbcTemplate),jdbcTemplate);
    }

    @Bean
    public BzNoHelper bzNoHelper(SpringBootKitNoGenProperties properties, NoGenDao noGenDao, RedissonClient client){
        return new BzNoHelper(properties,client,noGenDao);
    }
    @Bean
    public SpringBootKitNoGenAutoInitDB springBootKitNoGenAutoInitDB(JdbcTemplate jdbcTemplate,NoGenDao noGenDao,SpringBootKitNoGenProperties properties){
        return new SpringBootKitNoGenAutoInitDB(jdbcTemplate,noGenDao,properties);
    }
    @Bean
    public SpringBootKitNoGenApi springBootKitNoGenApi(BzNoHelper bzNoHelper){
        return new SpringBootKitNoGenApi(bzNoHelper);
    }
    @Bean
    public SpringBootKitNoGenWebConfiguration springBootKitNoGenWebConfiguration(SpringBootKitNoGenProperties properties){
        return new SpringBootKitNoGenWebConfiguration(properties);
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
