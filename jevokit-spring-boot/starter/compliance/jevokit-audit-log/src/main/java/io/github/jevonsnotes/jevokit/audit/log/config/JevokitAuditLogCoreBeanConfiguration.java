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
 * 审计日志核心 bean 装配类：无条件注册 {@link AuditLogProperties}（@RefreshScope）。
 * 使用方式：随 starter 自动装配；主装配类 JevokitAuditLogConfiguration 受 enabled 开关控制，
 * 本类独立存在保证配置 bean 在模块关闭时仍可解析（供管理端读取/热刷新当前审计配置）。
 * <p>关键约束：@RefreshScope 支持配置中心热更新（event-types 规则、内容截断策略等即时生效，
 * 无需重启）。
 */
@Configuration
@Slf4j
public class JevokitAuditLogCoreBeanConfiguration {

    /** 注册审计配置（@RefreshScope，热更新语义见类注释）。 */
    @RefreshScope
    @Bean
    public AuditLogProperties auditLogProperties(){
        return new AuditLogProperties();
    }

}
