package springbootkit.audit.log.config;

import cn.hutool.core.io.IoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import springbootkit.audit.log.aspect.AuditLogAspect;
import springbootkit.audit.log.dao.AuditLogDao;
import springbootkit.audit.log.dao.H2SQLDao;
import springbootkit.audit.log.dao.MySQLDao;
import springbootkit.audit.log.dao.PostgreSQLDao;
import springbootkit.audit.log.filter.AuditLogFilter;
import springbootkit.audit.log.job.AuditLogAutoMigrateJob;
import springbootkit.audit.log.processor.AuditLogProcessor;
import springbootkit.audit.log.processor.DefaultAuditLogProcessor;
import springbootkit.configuration.audit.log.AuditLogProperties;
import springbootkit.configuration.config.IpConfiguration;
import springbootkit.core.consts.DBType;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */
@ConditionalOnProperty(
        prefix = AuditLogProperties.CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
@Configuration
@EnableScheduling
@Slf4j
//public class SpringBootKitAuditLogConfiguration implements WebMvcConfigurer {
public class SpringBootKitAuditLogConfiguration {
    private final AuditLogProperties properties;
    /**
     * 默认审计日志处理器
     * 可通过继承AuditLogProcessor实现
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogProcessor auditLogProcessor(){
        return new DefaultAuditLogProcessor();
    }

    @Bean
    @ConditionalOnMissingBean({AuditLogFilter.class})
    public FilterRegistrationBean<AuditLogFilter> auditLogFilterFilterRegistrationBean(AuditLogProcessor processor,AuditLogProperties properties,IpConfiguration ipConfiguration) {
        AuditLogFilter filter = new AuditLogFilter(processor,properties,ipConfiguration);
        FilterRegistrationBean<AuditLogFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        List<String> urlPatterns = new ArrayList<>();
        urlPatterns.add("/*");
        registrationBean.setUrlPatterns(urlPatterns);
        registrationBean.setName("auditLogFilter");
        registrationBean.setOrder(200);
        log.info("Audit Log is ready!");
        return registrationBean;
    }
//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        if(properties.isEnabled()){
//            List<String> urlPatterns = new ArrayList<>();
//            if (urlPatterns.isEmpty()) {
//                urlPatterns.add("/**");
//            }
//            AuditLogProcessor processor = SpringUtil.getBean(AuditLogProcessor.class);
//            AuditLogInterceptor interceptor = new AuditLogInterceptor(processor,properties,ipConfiguration());
//            registry.addInterceptor(interceptor)
//                    .addPathPatterns(urlPatterns)
//                    .order(Ordered.LOWEST_PRECEDENCE);
//            log.info("Audit Log is ready!");
//        }
//    }
    @Bean
    @ConditionalOnMissingBean
    public IpConfiguration ipConfiguration(){
        return new IpConfiguration();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(AuditLogProcessor auditLogProcessor,IpConfiguration ipConfiguration){
        return new AuditLogAspect(auditLogProcessor,properties,ipConfiguration);
    }
    @ConditionalOnBean(JdbcTemplate.class)
    @Bean
    public AuditLogDao auditLogDao(JdbcTemplate jdbcTemplate){
        return convert(getJdbcUrl(jdbcTemplate),jdbcTemplate);
    }

    @ConditionalOnBean(AuditLogDao.class)
    @Bean
    public SpringBootKitAuditLogAutoInitDB springBootKitAuditLogAutoInitDB(JdbcTemplate jdbcTemplate,AuditLogDao dao,AuditLogProperties properties){
        return new SpringBootKitAuditLogAutoInitDB(jdbcTemplate,dao,properties);
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
    private AuditLogDao convert(String jdbcUrl,JdbcTemplate jdbcTemplate){
        if(jdbcUrl.matches(".*"+ DBType.MYSQL.getJdbcName()+":.*")){
            return new MySQLDao(jdbcTemplate,properties);
        } else if(jdbcUrl.matches(".*"+ DBType.POSTGRESQL.getJdbcName()+":.*")){
            return new PostgreSQLDao(jdbcTemplate,properties);
        }  else if(jdbcUrl.matches(".*"+ DBType.H2.getJdbcName()+":.*")){
            return new H2SQLDao(jdbcTemplate,properties);
        } else {
            throw new UnsupportedOperationException("不支持的数据库类型");
        }
    }

    @Bean
    public AuditLogAutoMigrateJob auditLogAutoMigrateJob(AuditLogDao dao){
        return new AuditLogAutoMigrateJob(dao,properties);
    }

}
