package io.github.jevonsnotes.jevokit.audit.log.config;

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
import io.github.jevonsnotes.jevokit.audit.log.aspect.AuditLogAspect;
import io.github.jevonsnotes.jevokit.audit.log.dao.AuditLogDao;
import io.github.jevonsnotes.jevokit.audit.log.dao.H2SQLDao;
import io.github.jevonsnotes.jevokit.audit.log.dao.MySQLDao;
import io.github.jevonsnotes.jevokit.audit.log.dao.PostgreSQLDao;
import io.github.jevonsnotes.jevokit.audit.log.filter.AuditLogFilter;
import io.github.jevonsnotes.jevokit.audit.log.processor.AuditLogProcessor;
import io.github.jevonsnotes.jevokit.audit.log.processor.DefaultAuditLogProcessor;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.configuration.config.IpConfiguration;
import io.github.jevonsnotes.jevokit.core.consts.DBType;

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
public class JevokitAuditLogCoreBeanConfiguration {

    @RefreshScope
    @Bean
    public AuditLogProperties auditLogProperties(){
        return new AuditLogProperties();
    }

}
