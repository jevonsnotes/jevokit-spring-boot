package io.github.jevonsnotes.jevokit.audit.log.config;

import cn.hutool.core.io.IoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.github.jevonsnotes.jevokit.audit.log.aspect.AuditLogAspect;
import io.github.jevonsnotes.jevokit.audit.log.dao.AuditLogDao;
import io.github.jevonsnotes.jevokit.audit.log.dao.H2SQLDao;
import io.github.jevonsnotes.jevokit.audit.log.dao.MySQLDao;
import io.github.jevonsnotes.jevokit.audit.log.dao.PostgreSQLDao;
import io.github.jevonsnotes.jevokit.audit.log.filter.AuditLogFilter;
import io.github.jevonsnotes.jevokit.audit.log.job.AuditLogAutoMigrateJob;
import io.github.jevonsnotes.jevokit.audit.log.processor.AuditLogProcessor;
import io.github.jevonsnotes.jevokit.audit.log.processor.DefaultAuditLogProcessor;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.configuration.config.IpConfiguration;
import io.github.jevonsnotes.jevokit.core.consts.DBType;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计日志模块主装配类：注册处理器、URL 匹配过滤器、@AuditLog 切面，并按 JDBC URL
 * 选择方言 DAO（MySQL/PostgreSQL/H2）与建表、迁移等 DB 组件。
 * 使用方式：引入本 starter 默认开启（spkit.audit-log.enabled 未配置视为 true），过滤器按
 * event-types.patterns 把命中 URL 的请求记为审计事件；无 DB（无 JdbcTemplate）时降级为仅日志输出，
 * DB 相关 bean 整体不装配。
 * <p>关键约束：仅 SERVLET web 应用生效；不支持的数据库类型在装配期直接抛
 * {@link UnsupportedOperationException}（fail-fast，避免静默丢审计）；DAO/迁移 Job 均挂
 * @ConditionalOnBean 守卫与 JdbcTemplate 对齐（见各 bean 注释）。
 */
@ConditionalOnProperty(
        prefix = AuditLogProperties.CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
// 包名迁移（旧 kit 单段包 → io.github.jevonsnotes.jevokit groupId 反演包）后 FQCN 字母序翻转到
// JdbcTemplateAutoConfiguration 之前，@ConditionalOnBean(JdbcTemplate.class) 在模板
// bean 注册前求值而静默不装配——显式声明次序锚定，跨包前缀变化不再影响装配结果。
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@RequiredArgsConstructor
@Configuration
@EnableScheduling
@Slf4j
//public class JevokitAuditLogConfiguration implements WebMvcConfigurer {
public class JevokitAuditLogConfiguration {
    private final AuditLogProperties properties;
    /**
     * 默认审计日志处理器
     * 可通过继承AuditLogProcessor实现
     * @return
     */
    /** 注册默认处理器（业务实现 AuditLogProcessor 即可覆盖）。 */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogProcessor auditLogProcessor(){
        return new DefaultAuditLogProcessor();
    }

    /** 注册审计过滤器（/* 全路径、order=200，URL 规则命中才落审计）。 */
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
    /** 注册本机 IP/端口配置（审计记录的 localIp/port 来源）。 */
    @Bean
    @ConditionalOnMissingBean
    public IpConfiguration ipConfiguration(){
        return new IpConfiguration();
    }

    /** 注册 @AuditLog 切面。 */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(AuditLogProcessor auditLogProcessor,IpConfiguration ipConfiguration){
        return new AuditLogAspect(auditLogProcessor,properties,ipConfiguration);
    }
    /**
     * 按 JDBC URL 选择方言 DAO 注册（MySQL/PostgreSQL/H2）。
     *
     * @throws UnsupportedOperationException 数据库类型不在支持列表时抛出（fail-fast，防静默丢审计）
     */
    @ConditionalOnBean(JdbcTemplate.class)
    @Bean
    public AuditLogDao auditLogDao(JdbcTemplate jdbcTemplate){
        return convert(getJdbcUrl(jdbcTemplate),jdbcTemplate);
    }

    /** 注册启动建表组件（依赖 DAO 存在，守卫理由见下方迁移 Job 注释）。 */
    @ConditionalOnBean(AuditLogDao.class)
    @Bean
    public JevokitAuditLogAutoInitDB jevokitAuditLogAutoInitDB(JdbcTemplate jdbcTemplate,AuditLogDao dao,AuditLogProperties properties){
        return new JevokitAuditLogAutoInitDB(jdbcTemplate,dao,properties);
    }

    /**
     * 取当前数据源的 JDBC URL（方言判定依据；连接即取即还，异常上抛 fail-fast）。
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
     * 按 JDBC URL 前缀选择方言 DAO：mysql:/postgresql:/h2: 三选一。
     *
     * @throws UnsupportedOperationException 其他数据库类型抛出（宁可启动失败不静默丢审计）
     */
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

    /**
     * 自动迁移 job 依赖 AuditLogDao（无 DB 环境不存在），须与 dao 的
     * {@code @ConditionalOnBean(JdbcTemplate.class)} 守卫对齐，否则无 DB 应用整个模块装配失败
     */
    /** 注册每日迁移 Job（依赖 DAO，守卫对齐见上方注释）。 */
    @ConditionalOnBean(AuditLogDao.class)
    @Bean
    public AuditLogAutoMigrateJob auditLogAutoMigrateJob(AuditLogDao dao){
        return new AuditLogAutoMigrateJob(dao,properties);
    }

}
