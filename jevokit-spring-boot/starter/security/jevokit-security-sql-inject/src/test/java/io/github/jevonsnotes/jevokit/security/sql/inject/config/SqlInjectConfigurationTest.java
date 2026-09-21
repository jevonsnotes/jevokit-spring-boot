package io.github.jevonsnotes.jevokit.security.sql.inject.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import io.github.jevonsnotes.jevokit.configuration.config.JevokitCoreConfiguration;
import io.github.jevonsnotes.jevokit.security.sql.inject.chain.SqlInjectStringPropertiesEditChain;
import io.github.jevonsnotes.jevokit.security.sql.inject.handler.FormSqlInjectHandler;
import io.github.jevonsnotes.jevokit.security.sql.inject.handler.RequestBodySqlInjectHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlInjectConfiguration 装配条件：jevokit.security.sql-inject.enabled 开关切换 bean 存在性。
 */
class SqlInjectConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JevokitCoreConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    SqlInjectConfiguration.class));

    @Test
    @DisplayName("默认（无配置，matchIfMissing=true）：全部 sql-inject bean 装配")
    void beansExistByDefault() {
        runner.run(context -> {
            assertThat(context).as("默认应装配 RequestBodySqlInjectHandler").hasSingleBean(RequestBodySqlInjectHandler.class);
            assertThat(context).as("默认应装配 FormSqlInjectHandler").hasSingleBean(FormSqlInjectHandler.class);
            assertThat(context).as("默认应装配 SqlInjectStringPropertiesEditChain").hasSingleBean(SqlInjectStringPropertiesEditChain.class);
        });
    }

    @Test
    @DisplayName("jevokit.security.sql-inject.enabled=true：全部 bean 装配")
    void beansExistWhenEnabled() {
        runner.withPropertyValues("jevokit.security.sql-inject.enabled=true").run(context ->
                assertThat(context).as("显式开启应装配 RequestBodySqlInjectHandler").hasSingleBean(RequestBodySqlInjectHandler.class));
    }

    @Test
    @DisplayName("jevokit.security.sql-inject.enabled=false：全部 bean 不装配")
    void beansAbsentWhenDisabled() {
        runner.withPropertyValues("jevokit.security.sql-inject.enabled=false").run(context -> {
            assertThat(context).as("关闭后不得装配 RequestBodySqlInjectHandler").doesNotHaveBean(RequestBodySqlInjectHandler.class);
            assertThat(context).as("关闭后不得装配 FormSqlInjectHandler").doesNotHaveBean(FormSqlInjectHandler.class);
            assertThat(context).as("关闭后不得装配 SqlInjectStringPropertiesEditChain").doesNotHaveBean(SqlInjectStringPropertiesEditChain.class);
        });
    }
}
