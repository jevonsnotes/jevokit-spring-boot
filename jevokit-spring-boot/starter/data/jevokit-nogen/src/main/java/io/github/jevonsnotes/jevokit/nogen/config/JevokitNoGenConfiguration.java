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
 * nogen 自动装配核心：按 jdbc url 自动路由库种 DAO（H2/MySQL/PG），装配 BzNoHelper 发号
 * 门面、自动建表器、API 与访问拦截器。
 *
 * <p>关键约束：Bean 方法参数直接依赖 JdbcTemplate/RedissonClient（无 @ConditionalOnBean
 * 守卫）——引入本 starter 即要求数据源与 Redisson 在位，缺任一将启动失败（有意为之：
 * 发号组件缺存储无从降级）。
 */
@Configuration
@Slf4j
public class JevokitNoGenConfiguration{
    @Bean
    /**
    * 配置 Bean。
    * @return spkit.nogen 绑定实例
    */
    public JevokitNoGenProperties jevokitNoGenProperties(){
        return new JevokitNoGenProperties();
    }
    @Bean
    /**
    * 按 jdbc url 方言路由库种 DAO（H2/MySQL/PG）。
    * @param jdbcTemplate 数据源模板（取连接元数据判型）
    * @return 对应方言 DAO 实现
    */
    public NoGenDao noGenDao(JdbcTemplate jdbcTemplate){
        return convert(getJdbcUrl(jdbcTemplate),jdbcTemplate);
    }

    @Bean
    /**
    * 发号门面 Bean。
    * @param properties 配置
    * @param noGenDao 持久化 DAO
    * @param client Redisson 客户端（序列/回收池载体）
    * @return 发号门面
    */
    public BzNoHelper bzNoHelper(JevokitNoGenProperties properties, NoGenDao noGenDao, RedissonClient client){
        return new BzNoHelper(properties,client,noGenDao);
    }
    @Bean
    /**
    * 自动建表器 Bean。
    * @param jdbcTemplate 数据源模板
    * @param noGenDao 方言 DAO
    * @param properties 配置
    * @return 建表器
    */
    public JevokitNoGenAutoInitDB jevokitNoGenAutoInitDB(JdbcTemplate jdbcTemplate,NoGenDao noGenDao,JevokitNoGenProperties properties){
        return new JevokitNoGenAutoInitDB(jdbcTemplate,noGenDao,properties);
    }
    @Bean
    /**
    * 发号 API Bean。
    * @param bzNoHelper 发号门面
    * @return API 控制器
    */
    public JevokitNoGenApi jevokitNoGenApi(BzNoHelper bzNoHelper){
        return new JevokitNoGenApi(bzNoHelper);
    }
    @Bean
    /**
    * Web 拦截装配 Bean。
    * @param properties 配置（accessToken 校验来源）
    * @return WebMvcConfigurer
    */
    public JevokitNoGenWebConfiguration jevokitNoGenWebConfiguration(JevokitNoGenProperties properties){
        return new JevokitNoGenWebConfiguration(properties);
    }

    /**
    * 从数据源连接元数据取 jdbc url。
    */
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
    /**
    * 按 url 前缀路由库种 DAO（h2/mysql/postgresql，未识别抛异常）。
    */
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
