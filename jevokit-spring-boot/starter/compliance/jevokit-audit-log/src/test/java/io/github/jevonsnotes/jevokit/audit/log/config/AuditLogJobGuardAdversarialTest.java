package io.github.jevonsnotes.jevokit.audit.log.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import io.github.jevonsnotes.jevokit.audit.log.job.AuditLogAutoMigrateJob;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-7 对抗性复测（终验 #6）：开发用例已锁「无 DB → dao/job 双跳过、处理器照常」；
 * 本类补刀反向——<b>有 DB 时迁移 job 必须仍被装配</b>。
 *
 * <p>攻击假设：修复给 job 加的 {@code @ConditionalOnBean(AuditLogDao.class)} 守卫依赖
 * 同类内 bean 定义顺序与自动装配顺序——若条件因顺序问题静默恒 false（过度抑制），
 * 有 DB 的应用会<b>无声丢掉</b>自动迁移 job（表不建、无报错），比装配失败更难发现。
 * 开发用例只断言了 dao 存在，未断言 job 存在——这正是本用例要堵的缝。
 */
@DisplayName("BUG-7 复测：有 DB 时迁移 job 不被守卫过度抑制（对抗性）")
class AuditLogJobGuardAdversarialTest {

    @Test
    @DisplayName("JdbcTemplate + H2 在位：dao 路由 H2 且 AuditLogAutoMigrateJob 照常装配")
    void migrateJobStillWiredWhenDaoPresent() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JdbcTemplateAutoConfiguration.class, JevokitAuditLogConfiguration.class))
                .withBean(cn.hutool.extra.spring.SpringUtil.class)
                .withBean(AuditLogProperties.class, AuditLogProperties::new)
                .withBean("dataSource", SimpleDriverDataSource.class, () -> {
                    SimpleDriverDataSource ds = new SimpleDriverDataSource();
                    ds.setDriver(new org.h2.Driver());
                    ds.setUrl("jdbc:h2:mem:auditjobadv;DB_CLOSE_DELAY=-1");
                    ds.setUsername("sa");
                    ds.setPassword("");
                    return ds;
                })
                .run(context -> {
                    assertThat(context).as("有 DB 环境上下文应正常启动").hasNotFailed();
                    assertThat(context).as("前置：AuditLogDao 应在位（守卫条件成立的前提）")
                            .hasSingleBean(io.github.jevonsnotes.jevokit.audit.log.dao.AuditLogDao.class);
                    assertThat(context)
                            .as("守卫不得过度抑制：dao 在位时 AuditLogAutoMigrateJob 必须装配"
                                    + "（静默丢失 = 无报错不建表，比装配失败更隐蔽）")
                            .hasSingleBean(AuditLogAutoMigrateJob.class);
                });
    }
}
