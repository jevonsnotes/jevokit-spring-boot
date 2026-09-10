package springbootkit.audit.log.config;

import cn.hutool.core.io.IoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
import springbootkit.audit.log.aspect.AuditLogAspect;
import springbootkit.audit.log.dao.AuditLogDao;
import springbootkit.audit.log.dao.H2SQLDao;
import springbootkit.audit.log.dao.MySQLDao;
import springbootkit.audit.log.dao.PostgreSQLDao;
import springbootkit.audit.log.filter.AuditLogFilter;
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
@Configuration
@Slf4j
public class SpringBootKitAuditLogCoreBeanConfiguration {

    @RefreshScope
    @Bean
    public AuditLogProperties auditLogProperties(){
        return new AuditLogProperties();
    }

}
