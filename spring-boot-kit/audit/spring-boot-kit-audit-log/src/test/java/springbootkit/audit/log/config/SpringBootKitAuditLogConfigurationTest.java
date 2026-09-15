package springbootkit.audit.log.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import springbootkit.audit.log.dao.AuditLogDao;
import springbootkit.audit.log.dao.H2SQLDao;
import springbootkit.audit.log.processor.AuditLogProcessor;
import springbootkit.audit.log.processor.DefaultAuditLogProcessor;
import springbootkit.configuration.audit.log.AuditLogProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * audit-log 自动装配条件单测（B 档测试重构 #5）。
 * WebApplicationContextRunner（配置类要求 SERVLET web 环境）+ H2 内存库，
 * 覆盖：默认开（matchIfMissing）、显式关、H2 dao 路由、核心 bean 存在性。
 */
class SpringBootKitAuditLogConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JdbcTemplateAutoConfiguration.class, SpringBootKitAuditLogConfiguration.class))
            .withBean("dataSource", SimpleDriverDataSource.class, () -> {
                SimpleDriverDataSource ds = new SimpleDriverDataSource();
                ds.setDriver(new org.h2.Driver());
                ds.setUrl("jdbc:h2:mem:auditlogtest;DB_CLOSE_DELAY=-1");
                ds.setUsername("sa");
                ds.setPassword("");
                return ds;
            })
            .withBean(AuditLogProperties.class, AuditLogProperties::new);

    @Test
    @DisplayName("web 环境 + 默认开关：全套审计 bean 成套装配且 dao 路由 H2 实现")
    void wiresAuditBeansWithH2DaoByDefault() {
        runner.run(context -> {
            assertThat(context).as("web 环境下上下文应正常启动").hasNotFailed();

            assertThat(context).as("默认处理器应装配").hasSingleBean(AuditLogProcessor.class);
            assertThat(context.getBean(AuditLogProcessor.class))
                    .as("默认实现应为 DefaultAuditLogProcessor").isInstanceOf(DefaultAuditLogProcessor.class);
            assertThat(context).as("过滤器注册 bean 应装配").hasSingleBean(FilterRegistrationBean.class);
            assertThat(context).as("JdbcTemplate 在位时 AuditLogDao 应装配").hasSingleBean(AuditLogDao.class);
            assertThat(context.getBean(AuditLogDao.class))
                    .as("jdbc:h2 url 应路由到 H2SQLDao").isInstanceOf(H2SQLDao.class);
            assertThat(context).as("切面应装配").hasSingleBean(springbootkit.audit.log.aspect.AuditLogAspect.class);
        });
    }

    @Test
    @DisplayName("spkit.audit.log.enabled=false：整组装配关闭")
    void disabledByProperty() {
        runner.withPropertyValues("spkit.audit.log.enabled=false")
                .run(context -> {
                    assertThat(context).as("关闭开关后上下文仍应正常启动").hasNotFailed();
                    assertThat(context).as("处理器不应装配").doesNotHaveBean(AuditLogProcessor.class);
                    assertThat(context).as("dao 不应装配").doesNotHaveBean(AuditLogDao.class);
                });
    }

    @Test
    @DisplayName("无 JdbcTemplate 时 dao/job 跳过且模块其余部分正常（BUG-7 修复后行为）")
    void daoSkippedWithoutJdbcTemplate() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringBootKitAuditLogConfiguration.class))
                .withBean(AuditLogProperties.class, AuditLogProperties::new)
                .run(context -> {
                    assertThat(context).as("无 DB 环境上下文应正常启动（job 守卫已对齐 dao）").hasNotFailed();
                    assertThat(context).as("处理器不依赖 DB，仍应装配")
                            .hasSingleBean(AuditLogProcessor.class);
                    assertThat(context).as("AuditLogDao 依赖 JdbcTemplate，应跳过")
                            .doesNotHaveBean(AuditLogDao.class);
                    assertThat(context).as("迁移 job 依赖 AuditLogDao，应同步跳过")
                            .doesNotHaveBean(springbootkit.audit.log.job.AuditLogAutoMigrateJob.class);
                });
    }

    @Test
    @DisplayName("类初始化顺序验收：无 Spring 环境强制加载 AuditLogDao 类不抛（终验裁定修复）")
    void auditLogDaoClassInitBeforeSpringUtilDoesNotExplode() throws Exception {
        // 原接口 static 字段 jdbcTemplate/PROPERTIES 在类初始化期 SpringUtil.getBean——
        // 无 DB/SpringUtil 未就绪时任何实现类（H2SQLDao 等）加载即抛 ExceptionInInitializerError。
        // 修复后为调用期惰性查找：Class.forName(initialize=true) 强制执行 <clinit>，
        // 若仍依赖 SpringUtil 将直接失败。装配期证据：本类各 runner 已不再注册 SpringUtil 仍绿
        Class.forName(AuditLogDao.class.getName(), true, AuditLogDao.class.getClassLoader());
    }
}
